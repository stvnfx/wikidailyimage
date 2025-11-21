package dev.sf13;

import dev.sf13.entity.PictureOfTheDay;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class PictureOfTheDayResourceTest {

    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            PictureOfTheDay.deleteAll();
        });
    }

    @Test
    public void testGetTodayEndpoint_Empty() {
        given()
          .when().get("/api/potd/today")
          .then()
             .statusCode(204);
    }

    @Test
    public void testGetTodayEndpoint_FoundToday() {
        QuarkusTransaction.requiringNew().run(() -> {
            PictureOfTheDay potd = new PictureOfTheDay();
            potd.date = LocalDate.now();
            potd.imageUrl = "http://example.com/img.jpg";
            potd.description = "Desc";
            potd.shortDescription = "Short Desc";
            potd.originalImage = new byte[]{1,2,3,4}; // Minimal image data, might fail image processing if real validation occurs
            potd.persist();
        });

        given()
          .when().get("/api/potd/today")
          .then()
             .statusCode(200)
             .body("description", is("Desc"));
    }

    @Test
    public void testGetTodayEndpoint_Fallback() {
        QuarkusTransaction.requiringNew().run(() -> {
            PictureOfTheDay potd = new PictureOfTheDay();
            potd.date = LocalDate.now().minusDays(2);
            potd.imageUrl = "http://example.com/old.jpg";
            potd.description = "Old Desc";
            potd.persist();
        });

        given()
          .when().get("/api/potd/today")
          .then()
             .statusCode(200)
             .body("description", is("Old Desc"));
    }

    @Test
    public void testTriggerScrape() {
        given()
            .when().post("/api/potd/scrape")
            .then()
            .statusCode(200);
    }

    @Test
    public void testGetTrmnlImage() {
        // Need valid image data for ImageIO to work
        // A simple 1x1 red pixel PNG
        byte[] validPng = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        QuarkusTransaction.requiringNew().run(() -> {
            PictureOfTheDay potd = new PictureOfTheDay();
            potd.date = LocalDate.now();
            potd.imageUrl = "http://example.com/img.png";
            potd.originalImage = validPng;
            potd.persist();
        });

        given()
            .when().get("/api/potd/today/trmnl")
            .then()
            .statusCode(200)
            .contentType("image/png");
    }
}
