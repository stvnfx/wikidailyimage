package dev.sf13.resource;

import dev.sf13.dto.PictureOfTheDayDTO;
import dev.sf13.entity.PictureOfTheDay;
import dev.sf13.service.WikipediaScraper;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;

@Path("/api/potd")
public class PictureOfTheDayResource {

    @Inject
    WikipediaScraper scraper;

    @GET
    @Path("/today")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<PictureOfTheDayDTO> getToday() {
        // Delegate to getByDate to reuse cache and logic
        return getByDate(LocalDate.now().toString());
    }

    @GET
    @Path("/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    @CacheResult(cacheName = "potd-date")
    public Uni<PictureOfTheDayDTO> getByDate(@PathParam("date") String dateStr) {
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (java.time.format.DateTimeParseException e) {
            return Uni.createFrom().failure(new jakarta.ws.rs.BadRequestException("Invalid date format. Use YYYY-MM-DD"));
        }

        final LocalDate finalDate = date;
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(finalDate);
            if (potd == null) return null;
            return new PictureOfTheDayDTO(
                potd.date,
                potd.description,
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
    public Uni<Response> getImage(@PathParam("date") String dateStr) {
         LocalDate date = LocalDate.parse(dateStr);
         return Uni.createFrom().item(() -> {
             PictureOfTheDay potd = PictureOfTheDay.findByDate(date);
             if (potd != null && potd.originalImage != null) {
                 return Response.ok(potd.originalImage).build();
             }
             return Response.status(Response.Status.NOT_FOUND).build();
         }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/{date}/image/dithered")
    @Produces("image/png")
    @CacheResult(cacheName = "potd-image-dithered")
    public Uni<Response> getDitheredImage(@PathParam("date") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        return Uni.createFrom().item(() -> {
            PictureOfTheDay potd = PictureOfTheDay.findByDate(date);
            if (potd != null && potd.ditheredImage != null) {
                return Response.ok(potd.ditheredImage).build();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/scrape")
    public Uni<Response> triggerScrape() {
        return Uni.createFrom().item(() -> {
            scraper.scrape();
            return Response.ok("Scrape triggered").build();
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }
}
