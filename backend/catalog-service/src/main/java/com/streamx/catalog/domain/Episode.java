package com.streamx.catalog.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "episodes", uniqueConstraints = @UniqueConstraint(
        name = "uk_episodes_season_number", columnNames = {"season_id", "episode_number"}))
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String synopsis;

    private Integer runtimeMinutes;
    private LocalDate releaseDate;
    private String thumbnailUrl;
    private String mediaAssetUrl;

    public Episode() {
    }

    public Episode(UUID id, UUID seasonId, Integer episodeNumber, String title, String synopsis,
                   Integer runtimeMinutes, LocalDate releaseDate, String thumbnailUrl, String mediaAssetUrl) {
        this.id = id;
        this.seasonId = seasonId;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.synopsis = synopsis;
        this.runtimeMinutes = runtimeMinutes;
        this.releaseDate = releaseDate;
        this.thumbnailUrl = thumbnailUrl;
        this.mediaAssetUrl = mediaAssetUrl;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(UUID seasonId) {
        this.seasonId = seasonId;
    }

    public Integer getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
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

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Integer runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getMediaAssetUrl() {
        return mediaAssetUrl;
    }

    public void setMediaAssetUrl(String mediaAssetUrl) {
        this.mediaAssetUrl = mediaAssetUrl;
    }
}
