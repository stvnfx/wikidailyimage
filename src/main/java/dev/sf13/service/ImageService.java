package dev.sf13.service;

import jakarta.enterprise.context.ApplicationScoped;
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

@ApplicationScoped
public class ImageService {

    @ConfigProperty(name = "wikipedia.user-agent")
    String userAgent;

    public byte[] downloadImage(String url) throws IOException {
        java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    public byte[] scaleImage(byte[] imageData, Integer width, Integer height) throws IOException {
        if (width == null && height == null) {
            return imageData;
        }
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
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

    public byte[] ditherImage(byte[] originalImageData) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalImageData));
        BufferedImage dithered = applyFloydSteinbergDithering(original);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(dithered, "png", baos);
        return baos.toByteArray();
    }

    private BufferedImage applyFloydSteinbergDithering(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dithered = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        // Convert to grayscale and handle dithering
        // We'll use a float array to process errors to avoid clipping issues during propagation
        float[][] pixels = new float[w][h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(img.getRGB(x, y));
                // Simple grayscale conversion
                float gray = (c.getRed() + c.getGreen() + c.getBlue()) / 3.0f;
                pixels[x][y] = gray;
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float oldPixel = pixels[x][y];
                float newPixel = oldPixel < 128 ? 0 : 255;
                pixels[x][y] = newPixel;

                float quantError = oldPixel - newPixel;

                if (x + 1 < w)
                    pixels[x + 1][y] = pixels[x + 1][y] + quantError * 7 / 16;
                if (x - 1 >= 0 && y + 1 < h)
                    pixels[x - 1][y + 1] = pixels[x - 1][y + 1] + quantError * 3 / 16;
                if (y + 1 < h)
                    pixels[x][y + 1] = pixels[x][y + 1] + quantError * 5 / 16;
                if (x + 1 < w && y + 1 < h)
                    pixels[x + 1][y + 1] = pixels[x + 1][y + 1] + quantError * 1 / 16;
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = (int) Math.min(255, Math.max(0, pixels[x][y]));
                int rgb = new Color(val, val, val).getRGB();
                dithered.setRGB(x, y, rgb);
            }
        }

        return dithered;
    }
}
