package dev.sf13.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "picture_of_the_day")
public class PictureOfTheDay extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public LocalDate date;

    @Column(length = 5000)
    public String description;

    @Column(length = 1000)
    public String shortDescription;

    public String credit;

    public String imageUrl;

    @Lob
    public byte[] originalImage;

    @Lob
    public byte[] ditheredImage;

    public LocalDateTime createdAt;

    public static PictureOfTheDay findByDate(LocalDate date) {
        return find("date", date).firstResult();
    }

    public static PictureOfTheDay findByImageUrl(String imageUrl) {
        return find("imageUrl", imageUrl).firstResult();
    }

    public static PictureOfTheDay findLatest() {
        return find("order by date desc").firstResult();
    }
}
