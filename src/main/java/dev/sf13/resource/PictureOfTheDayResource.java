package dev.sf13.resource;

import dev.sf13.dto.PictureOfTheDayDTO;
import dev.sf13.entity.PictureOfTheDay;
import dev.sf13.service.WikipediaScraper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.cache.CacheResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

@Path("/api/potd")
public class PictureOfTheDayResource {
    private static final Logger LOG = Logger.getLogger(PictureOfTheDayResource.class);

    @Inject
    WikipediaScraper scraper;

    @Inject
    dev.sf13.service.ImageService imageService;

    @Inject
    MeterRegistry registry;

    @GET
    @Path("/today")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Uni<PictureOfTheDayDTO> getToday() {
        registry.counter("potd.requests", Tags.of("type", "today")).increment();
        LOG.info("GET /api/potd/today");
        LocalDate today = LocalDate.now();
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(today);
            if (potd == null) {
                LOG.warnf("POTD for today (%s) not found. Attempting fallback to latest available image.", today);
                potd = PictureOfTheDay.findLatest();
                if (potd != null) {
                    LOG.infof("Fallback successful. Serving POTD from %s", potd.date);
                } else {
                    LOG.error("Fallback failed. No POTD records found in database.");
                }
            } else {
                LOG.infof("Serving POTD for today: %s", today);
            }

            if (potd == null) {
                return null;
            }
            return new PictureOfTheDayDTO(
                potd.date,
                potd.description,
                potd.shortDescription,
                potd.credit,
                "/api/potd/" + potd.date.toString() + "/image",
                "/api/potd/" + potd.date.toString() + "/image/dithered"
            );
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @CacheResult(cacheName = "potd-date")
    public Uni<PictureOfTheDayDTO> getByDate(@PathParam("date") String dateStr) {
        registry.counter("potd.requests", Tags.of("type", "date")).increment();
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (java.time.format.DateTimeParseException e) {
            LOG.errorf("Invalid date format received: %s", dateStr);
            return Uni.createFrom().failure(new jakarta.ws.rs.BadRequestException("Invalid date format. Use YYYY-MM-DD"));
        }

        final LocalDate finalDate = date;
        LOG.info("GET /api/potd/" + finalDate);
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(finalDate);
            if (potd == null) {
                LOG.warnf("POTD not found for date: %s", finalDate);
                return null;
            }
            return new PictureOfTheDayDTO(
                potd.date,
                potd.description,
                potd.shortDescription,
                potd.credit,
                "/api/potd/" + finalDate.toString() + "/image",
                "/api/potd/" + finalDate.toString() + "/image/dithered"
            );
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/{date}/image")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image")
    @Transactional
    public Uni<Response> getImage(@PathParam("date") String dateStr) {
         return getImageScaled(dateStr, null, null);
    }

    @GET
    @Path("/{date}/{width}/image")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-scaled-w")
    @Transactional
    public Uni<Response> getImageWidth(@PathParam("date") String dateStr, @PathParam("width") Integer width) {
        return getImageScaled(dateStr, width, null);
    }

    @GET
    @Path("/{date}/{width}/{height}/image")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-scaled-wh")
    @Transactional
    public Uni<Response> getImageWidthHeight(@PathParam("date") String dateStr, @PathParam("width") Integer width, @PathParam("height") Integer height) {
        return getImageScaled(dateStr, width, height);
    }

    private Uni<Response> getImageScaled(String dateStr, Integer width, Integer height) {
        LocalDate date = LocalDate.parse(dateStr);
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(date);
            if (potd != null && potd.originalImage != null) {
                try {
                    byte[] data = imageService.scaleImage(potd.originalImage, width, height);
                    return Response.ok(data).build();
                } catch (java.io.IOException e) {
                    LOG.error("Error scaling image", e);
                    return Response.serverError().build();
                }
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/{date}/image/dithered")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-dithered")
    @Transactional
    public Uni<Response> getDitheredImage(@PathParam("date") String dateStr) {
        return getDitheredImageScaled(dateStr, null, null);
    }

    @GET
    @Path("/{date}/{width}/image/dithered")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-dithered-scaled-w")
    @Transactional
    public Uni<Response> getDitheredImageWidth(@PathParam("date") String dateStr, @PathParam("width") Integer width) {
        return getDitheredImageScaled(dateStr, width, null);
    }

    @GET
    @Path("/{date}/{width}/{height}/image/dithered")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-dithered-scaled-wh")
    @Transactional
    public Uni<Response> getDitheredImageWidthHeight(@PathParam("date") String dateStr, @PathParam("width") Integer width, @PathParam("height") Integer height) {
        return getDitheredImageScaled(dateStr, width, height);
    }

    private Uni<Response> getDitheredImageScaled(String dateStr, Integer width, Integer height) {
        LocalDate date = LocalDate.parse(dateStr);
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(date);
            if (potd != null && potd.ditheredImage != null) {
                try {
                    byte[] data = imageService.scaleImage(potd.ditheredImage, width, height);
                    return Response.ok(data).build();
                } catch (java.io.IOException e) {
                    LOG.error("Error scaling image", e);
                    return Response.serverError().build();
                }
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/today/trmnl")
    @Produces("image/png")
    @Transactional
    public Uni<Response> getTrmnlImage() {
        registry.counter("potd.requests", Tags.of("type", "trmnl")).increment();
        LOG.info("GET /api/potd/today/trmnl");
        return Uni.createFrom().item(() -> {
            LocalDate today = LocalDate.now();
            PictureOfTheDay potd = PictureOfTheDay.findByDate(today);
            if (potd == null) {
                LOG.warnf("TRMNL Request: POTD for today (%s) not found. Checking for latest.", today);
                potd = PictureOfTheDay.findLatest();
            }
            if (potd != null) {
                LOG.infof("TRMNL Request: Serving POTD from date %s", potd.date);
                return potd.date.toString();
            }
            LOG.error("TRMNL Request: No POTD found.");
            return null;
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
          .flatMap(dateStr -> {
              if (dateStr == null) {
                  return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
              }
              return getTrmnlImageByDate(dateStr);
          });
    }

    @CacheResult(cacheName = "potd-trmnl-date")
    @Transactional
    public Uni<Response> getTrmnlImageByDate(String dateStr) {
        LOG.debugf("Generating/Retrieving cached TRMNL image for date: %s", dateStr);
        LocalDate date = LocalDate.parse(dateStr);
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(date);
            if (potd != null && potd.originalImage != null) {
                try {
                    // Scale to 800x480 first, preserving aspect ratio
                    byte[] scaled = imageService.scaleImageAndCenter(potd.originalImage, 800, 480);
                    // Then dither
                    byte[] dithered = imageService.ditherImage(scaled);
                    LOG.infof("TRMNL image generated for date: %s", dateStr);
                    return Response.ok(dithered).build();
                } catch (java.io.IOException e) {
                    LOG.error("Error generating TRMNL image", e);
                    return Response.serverError().build();
                }
            }
            LOG.warnf("TRMNL image generation failed. POTD or image data missing for date: %s", dateStr);
            return Response.status(Response.Status.NOT_FOUND).build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/scrape")
    public Uni<Response> triggerScrape() {
        registry.counter("scraper.triggered").increment();
        return Uni.createFrom().item(() -> {
            scraper.scrape();
            return Response.ok("Scrape triggered").build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }
}
