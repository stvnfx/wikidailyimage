package dev.sf13.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import javax.imageio.ImageIO;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.DistributionSummary;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImageService {

    @ConfigProperty(name = "wikipedia.user-agent")
    String userAgent;

    @Inject
    MeterRegistry registry;

    @WithSpan("ImageService.downloadImage")
    public byte[] downloadImage(String url) throws IOException {
        Log.infof("Downloading image from %s", url);
        java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            Log.infof("Downloaded %d bytes from %s", bytes.length, url);

            DistributionSummary.builder("image.download.size")
                .description("Size of downloaded images in bytes")
                .baseUnit("bytes")
                .register(registry)
                .record(bytes.length);

            return bytes;
        }
    }

    @WithSpan("ImageService.scaleImage")
    public byte[] scaleImage(byte[] imageData, Integer width, Integer height) throws IOException {
        Log.debugf("Scaling image to width=%s, height=%s", width, height);
        if (width == null && height == null) {
            return imageData;
        }
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
        if (original == null) {
            throw new IOException("Failed to read image data during scaling. The data may be corrupted or in an unsupported format.");
        }
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        int newWidth = originalWidth;
        int newHeight = originalHeight;

        if (width != null && height != null) {
            newWidth = width;
            newHeight = height;
        } else if (width != null) {
            newWidth = width;
            newHeight = (int) (((double) width / originalWidth) * originalHeight);
        } else if (height != null) {
            newHeight = height;
            newWidth = (int) (((double) height / originalHeight) * originalWidth);
        }

        Image scaled = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage bufferedScaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        bufferedScaled.getGraphics().drawImage(scaled, 0, 0, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedScaled, "png", baos);
        return baos.toByteArray();
    }

    @WithSpan("ImageService.scaleImageAndCenter")
    public byte[] scaleImageAndCenter(byte[] imageData, int targetWidth, int targetHeight) throws IOException {
        Log.debugf("Scaling and centering image to %dx%d (Cover Mode)", targetWidth, targetHeight);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
        if (original == null) {
            throw new IOException("Failed to read image data during scaling and centering. The data may be corrupted or in an unsupported format.");
        }

        // --- CHANGE 1: Logic Swap from "Fit" to "Fill" ---
        // We calculate the scale factor required for both width and height.
        // To FILL the screen, we pick the LARGER scale factor.
        // (To "fit" with borders, you would pick the smaller one).
        double scaleWidth = (double) targetWidth / original.getWidth();
        double scaleHeight = (double) targetHeight / original.getHeight();
        double scale = Math.max(scaleWidth, scaleHeight);

        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);
        // ------------------------------------------------

        BufferedImage background = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = background.createGraphics();

        // --- CHANGE 2: Keep the High Quality settings to prevent the Grid ---
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill background (just in case, though the image should cover it now)
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetWidth, targetHeight);

        // Calculate center position
        // Since newWidth/Height are >= target, these values will be 0 or negative
        // which correctly "shifts" the image to center it.
        int x = (targetWidth - newWidth) / 2;
        int y = (targetHeight - newHeight) / 2;

        g2d.drawImage(original, x, y, newWidth, newHeight, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(background, "png", baos);
        return baos.toByteArray();
    }

    @WithSpan("ImageService.ditherImage")
    public byte[] ditherImage(byte[] originalImageData) throws IOException {
        Log.debug("Starting Floyd-Steinberg dithering...");
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalImageData));
        if (original == null) {
            throw new IOException("Failed to read image data during dithering. The data may be corrupted or in an unsupported format.");
        }
        BufferedImage dithered = applyFloydSteinbergDithering(original);
        Log.debug("Dithering complete.");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(dithered, "png", baos);
        return baos.toByteArray();
    }

    private BufferedImage applyFloydSteinbergDithering(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // 1. Prepare the float array for error propagation
        float[][] pixels = new float[w][h];

        // 2. Convert to Grayscale AND add Noise
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(img.getRGB(x, y));

                // Use Luma formula (Human eyes are more sensitive to Green)
                float gray = (c.getRed() * 0.299f + c.getGreen() * 0.587f + c.getBlue() * 0.114f);

                // --- NOISE INJECTION START ---
                // Add a tiny bit of random noise (approx +/- 10 on a 0-255 scale)
                // This prevents the "worm" artifacts in flat gray areas by breaking the mathematical pattern.
                float noise = (float) ((Math.random() - 0.5) * 10);
                gray = gray + noise;

                // Clamp strictly between 0 and 255 just in case noise pushed it over
                gray = Math.max(0, Math.min(255, gray));
                // --- NOISE INJECTION END ---

                pixels[x][y] = gray;
            }
        }

        // 3. Apply Floyd-Steinberg Error Diffusion
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float oldPixel = pixels[x][y];
                float newPixel = oldPixel < 128 ? 0 : 255;
                pixels[x][y] = newPixel;

                float quantError = oldPixel - newPixel;

                // Distribute error to neighbors (7, 3, 5, 1 weights)
                if (x + 1 < w)
                    pixels[x + 1][y] += quantError * 7 / 16;
                if (x - 1 >= 0 && y + 1 < h)
                    pixels[x - 1][y + 1] += quantError * 3 / 16;
                if (y + 1 < h)
                    pixels[x][y + 1] += quantError * 5 / 16;
                if (x + 1 < w && y + 1 < h)
                    pixels[x + 1][y + 1] += quantError * 1 / 16;
            }
        }

        // 4. Output as strictly 1-bit Binary (Crucial for TRMNL)
        BufferedImage dithered = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Since we already quantized to 0 or 255 in the loop above,
                // we just map those to Black or White.
                int val = (int) pixels[x][y];
                // Safety clamp again just to be sure
                val = Math.min(255, Math.max(0, val));

                int rgb = (val < 128) ? Color.BLACK.getRGB() : Color.WHITE.getRGB();
                dithered.setRGB(x, y, rgb);
            }
        }

        return dithered;
    }
}
