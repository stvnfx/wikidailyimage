package dev.sf13.dto;

import java.time.LocalDate;

public class PictureOfTheDayDTO {
    public LocalDate date;
    public String description;
    public String shortDescription;
    public String credit;
    public String imageUrl;
    public String ditheredImageUrl;
    public String trmnlImageUrl;

    public PictureOfTheDayDTO() {}

    public PictureOfTheDayDTO(LocalDate date, String description, String shortDescription,
                              String credit, String imageUrl, String ditheredImageUrl, String trmnlImageUrl) {
        this.date = date;
        this.description = description;
        this.shortDescription = shortDescription;
        this.credit = credit;
        this.imageUrl = imageUrl;
        this.ditheredImageUrl = ditheredImageUrl;
        this.trmnlImageUrl = trmnlImageUrl;
    }
}
