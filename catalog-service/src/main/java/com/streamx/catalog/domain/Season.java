package com.streamx.catalog.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tvShowId;

    @Column(nullable = false)
    private Integer seasonNumber;

    private String title;

    @Column(length = 2000)
    private String synopsis;

    private LocalDate releaseDate;
    private String posterUrl;

    public Season() {
    }

    public Season(UUID id, UUID tvShowId, Integer seasonNumber, String title, String synopsis, LocalDate releaseDate, String posterUrl) {
        this.id = id;
        this.tvShowId = tvShowId;
        this.seasonNumber = seasonNumber;
        this.title = title;
        this.synopsis = synopsis;
        this.releaseDate = releaseDate;
        this.posterUrl = posterUrl;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTvShowId() {
        return tvShowId;
    }

    public void setTvShowId(UUID tvShowId) {
        this.tvShowId = tvShowId;
    }

    public Integer getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(Integer seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }
}
