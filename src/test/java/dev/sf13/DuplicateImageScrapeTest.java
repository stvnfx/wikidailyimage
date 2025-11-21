package dev.sf13;

import dev.sf13.entity.PictureOfTheDay;
import dev.sf13.service.WikipediaPageFetcher;
import dev.sf13.service.WikipediaScraper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestProfile(DuplicateImageScrapeTest.TestProfile.class)
public class DuplicateImageScrapeTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "dev.sf13.service.WikipediaScraper/scrape/CircuitBreaker/enabled", "false",
                "dev.sf13.service.WikipediaScraper/scrape/Retry/enabled", "false",
                "dev.sf13.service.WikipediaScraper/scrape/RateLimit/enabled", "false"
            );
        }
    }

    @Inject
    WikipediaScraper scraper;

    @InjectMock
    dev.sf13.service.ImageService imageService;

    @InjectMock
    dev.sf13.service.DescriptionAiService descriptionAiService;

    @InjectMock
    WikipediaPageFetcher pageFetcher;

    @BeforeEach
    @Transactional
    void setup() {
        PictureOfTheDay.deleteAll();
    }

    @Test
    @Transactional
    public void testReuseExistingImage() throws IOException {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a4/My_Image.jpg";
        String thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/My_Image.jpg/300px-My_Image.jpg";

        // 1. Create an existing record with this image URL (but different date)
        PictureOfTheDay oldPotd = new PictureOfTheDay();
        oldPotd.date = LocalDate.now().minusDays(1);
        oldPotd.imageUrl = imageUrl;
        oldPotd.originalImage = new byte[]{1, 2, 3};
        oldPotd.ditheredImage = new byte[]{4, 5, 6};
        oldPotd.shortDescription = "Old AI Description";
        oldPotd.description = "Old Description";
        oldPotd.credit = "Old Credit";
        oldPotd.createdAt = LocalDateTime.now().minusDays(1);
        oldPotd.persist();

        // 2. Mock Page Fetcher to return a page with this image
        Document doc = new Document("https://en.wikipedia.org/wiki/Main_Page");
        Element mpTfp = doc.appendElement("div").attr("id", "mp-tfp");
        mpTfp.appendElement("img").attr("src", thumbUrl);
        mpTfp.appendText("New Description");

        when(pageFetcher.fetch(anyString(), anyString())).thenReturn(doc);

        // 3. Run scrape
        scraper.scrape();

        // 4. Verify interactions
        // Should NOT download image
        verify(imageService, never()).downloadImage(anyString());
        // Should NOT call AI service
        verify(descriptionAiService, never()).summarize(anyString());

        // 5. Verify new record created
        PictureOfTheDay newPotd = PictureOfTheDay.findByDate(LocalDate.now());
        if (newPotd == null) {
            // Debugging
            System.out.println("All POTDs: " + PictureOfTheDay.findAll().list());
        }
        assert newPotd != null;
        assert newPotd.imageUrl.equals(imageUrl);
        assert newPotd.originalImage.length == 3; // Copied from old
        assert newPotd.shortDescription.equals("Old AI Description"); // Copied
    }

    @Test
    @Transactional
    public void testNewImageDownload() throws IOException {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/b/b4/New_Image.jpg";
        String thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b4/New_Image.jpg/300px-New_Image.jpg";

        // 1. Mock Page Fetcher
        Document doc = new Document("https://en.wikipedia.org/wiki/Main_Page");
        Element mpTfp = doc.appendElement("div").attr("id", "mp-tfp");
        mpTfp.appendElement("img").attr("src", thumbUrl);
        mpTfp.appendText("New Description");

        when(pageFetcher.fetch(anyString(), anyString())).thenReturn(doc);
        when(imageService.downloadImage(imageUrl)).thenReturn(new byte[]{10, 20});
        when(imageService.ditherImage(any())).thenReturn(new byte[]{30, 40});
        when(descriptionAiService.summarize(anyString())).thenReturn("New AI Summary");

        // 2. Run scrape
        scraper.scrape();

        // 3. Verify interactions
        verify(imageService).downloadImage(imageUrl);
        verify(descriptionAiService).summarize("New Description");

        // 4. Verify new record
        PictureOfTheDay newPotd = PictureOfTheDay.findByDate(LocalDate.now());
        assert newPotd != null;
        assert newPotd.imageUrl.equals(imageUrl);
        assert newPotd.originalImage.length == 2;
        assert newPotd.shortDescription.equals("New AI Summary");
    }
}
