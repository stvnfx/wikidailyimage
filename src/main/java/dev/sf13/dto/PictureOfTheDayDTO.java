package dev.sf13.dto;

import java.time.LocalDate;

public class PictureOfTheDayDTO {
    public LocalDate date;
    public String description;
    public String credit;
    public String imageUrl;
    public String ditheredImageUrl;

    public PictureOfTheDayDTO() {}

    public PictureOfTheDayDTO(LocalDate date, String description, String credit, String imageUrl, String ditheredImageUrl) {
        this.date = date;
        this.description = description;
        this.credit = credit;
        this.imageUrl = imageUrl;
        this.ditheredImageUrl = ditheredImageUrl;
    }
}
