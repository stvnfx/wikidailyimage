package dev.sf13.service;

import dev.sf13.entity.PictureOfTheDay;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import io.smallrye.faulttolerance.api.RateLimit;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@ApplicationScoped
public class WikipediaScraper {

    @Inject
    ImageService imageService;

    @Inject
    DescriptionAiService descriptionAiService;

    @Inject
    WikipediaPageFetcher pageFetcher;

    @Inject
    MeterRegistry registry;

    // Gauge state
    private java.util.concurrent.atomic.AtomicLong lastSuccessfulScrapeTime = new java.util.concurrent.atomic.AtomicLong(0);

    @ConfigProperty(name = "wikipedia.url")
    String wikipediaUrl;

    @ConfigProperty(name = "wikipedia.user-agent")
    String userAgent;

    @jakarta.annotation.PostConstruct
    void init() {
        registry.gauge("scraper.last_success_timestamp", lastSuccessfulScrapeTime);
    }

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    @Retry(maxRetries = 3, delay = 10, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 1, delayUnit = ChronoUnit.HOURS)
    @RateLimit(value = 1, window = 10, windowUnit = ChronoUnit.MINUTES)
    @WithSpan("Scraper.scrape")
    public void scrape() {
        Timer.Sample sample = Timer.start(registry);
        Log.info("Starting daily Wikipedia Picture of the Day scrape...");
        try {
            LocalDate today = LocalDate.now();
            if (PictureOfTheDay.findByDate(today) != null) {
                Log.info("Picture of the Day for today already exists. Skipping.");
                registry.counter("scraper.execution", Tags.of("result", "skipped")).increment();
                sample.stop(registry.timer("scraper.duration", "result", "skipped"));
                return;
            }

            Log.info("Fetching Wikipedia Main Page from: " + wikipediaUrl);
            Document doc = pageFetcher.fetch(wikipediaUrl, userAgent);
            Element mpTfp = doc.getElementById("mp-tfp");

            if (mpTfp == null) {
                Log.error("Could not find 'mp-tfp' element on Wikipedia Main Page. The structure might have changed.");
                return;
            }

            // Extract Image URL
            Element imgElement = mpTfp.select("img").first();
            if (imgElement == null) {
                Log.error("No image found in 'mp-tfp' container.");
                return;
            }

            String imgUrl = imgElement.attr("src");
            if (imgUrl.startsWith("//")) {
                imgUrl = "https:" + imgUrl;
            }
            // Get the high res version if possible, but typically the thumb is linked.
            // Often the 'a' tag wrapping the img points to the file page.
            // For now we grab the src. To get higher res we might need to manipulate the URL (remove /thumb/ and the suffix)
            // Example: //upload.wikimedia.org/wikipedia/commons/thumb/a/a4/My_Image.jpg/300px-My_Image.jpg
            // Original: //upload.wikimedia.org/wikipedia/commons/a/a4/My_Image.jpg

            String originalImgUrl = getOriginalImageUrl(imgUrl);
            Log.infof("Found image URL: %s. Resolved to original URL: %s", imgUrl, originalImgUrl);

            // Extract Description and Credit
            // The layout is usually: Image on left, Text on right.
            // The text is in a container, or just following the image.
            // In `mp-tfp`, the structure varies but often text is in a table cell or just text nodes.
            // Based on the provided image, the text is to the right.
            // Let's try to get all text from mp-tfp.

            String description = mpTfp.text();
            String credit = "";

            String separator = null;
            if (description.contains("Photograph credit:")) {
                separator = "Photograph credit:";
            } else if (description.contains("Photograph:")) {
                separator = "Photograph:";
            }

            if (separator != null) {
                String[] parts = description.split(separator);
                if (parts.length > 1) {
                    credit = parts[1].trim();
                    // Sometimes there are other things after credit, like "Recently featured".
                    // We might want to split by newline or just take until end.
                    // Let's refine description to remove credit and "Recently featured"
                    description = parts[0].trim();
                }
            }

            // Further cleanup of description if needed
            // e.g. removing "Today's featured picture" title if it was grabbed (usually it's in a header outside mp-tfp, but `mp-tfp` is the content)

            PictureOfTheDay existingPotd = PictureOfTheDay.findByImageUrl(originalImgUrl);
            byte[] originalImage;
            byte[] ditheredImage;
            String shortDescription;

            if (existingPotd != null) {
                Log.infof("Image already exists in database (Date: %s). Reusing binary data and AI summary.", existingPotd.date);
                originalImage = existingPotd.originalImage;
                ditheredImage = existingPotd.ditheredImage;
                shortDescription = existingPotd.shortDescription;
            } else {
                Log.infof("Image not found in database. Downloading from: %s", originalImgUrl);
                originalImage = imageService.downloadImage(originalImgUrl);
                Log.infof("Image downloaded. Size: %d bytes. Proceeding to dither.", originalImage.length);
                ditheredImage = imageService.ditherImage(originalImage);

                try {
                    Log.info("Generating short description via AI...");
                    shortDescription = descriptionAiService.summarize(description);
                    Log.info("AI Short description generated successfully.");
                } catch (Exception e) {
                    Log.error("Failed to generate short description via AI", e);
                    shortDescription = "Description unavailable";
                }
            }

            PictureOfTheDay potd = new PictureOfTheDay();
            potd.date = today;
            potd.description = description;
            potd.shortDescription = shortDescription;
            potd.credit = credit;
            potd.imageUrl = originalImgUrl;
            potd.originalImage = originalImage;
            potd.ditheredImage = ditheredImage;
            potd.createdAt = LocalDateTime.now();

            potd.persist();
            Log.info("Successfully scraped and saved Picture of the Day for " + today);
            lastSuccessfulScrapeTime.set(System.currentTimeMillis());
            registry.counter("scraper.execution", Tags.of("result", "success")).increment();
            sample.stop(registry.timer("scraper.duration", "result", "success"));

        } catch (IOException e) {
            Log.error("Error scraping Wikipedia", e);
            registry.counter("scraper.execution", Tags.of("result", "failure")).increment();
            sample.stop(registry.timer("scraper.duration", "result", "failure"));
        }
    }

    private String getOriginalImageUrl(String thumbUrl) {
        // Simple heuristic to try and get original image from thumb url
        // //upload.wikimedia.org/wikipedia/commons/thumb/x/xy/Name.jpg/300px-Name.jpg
        // -> //upload.wikimedia.org/wikipedia/commons/x/xy/Name.jpg

        if (thumbUrl.contains("/thumb/")) {
            int lastSlash = thumbUrl.lastIndexOf('/');
            if (lastSlash > 0) {
                 String potential = thumbUrl.substring(0, lastSlash);
                 // potential is now .../thumb/x/xy/Name.jpg
                 // We need to remove /thumb/
                 return potential.replace("/thumb/", "/");
            }
        }
        return thumbUrl;
    }
}
