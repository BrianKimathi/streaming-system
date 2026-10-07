package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Set;

public class CreateMovieRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String synopsis;
    private LocalDate releaseDate;
    private Integer runtimeMinutes;
    private String maturityRating;
    private String posterUrl;
    private String backdropUrl;
    private String trailerUrl;
    private String mediaAssetUrl;
    private ContentStatus status = ContentStatus.DRAFT;
    private Set<String> genreIds;

    public CreateMovieRequest() {
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

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Integer runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getMaturityRating() {
        return maturityRating;
    }

    public void setMaturityRating(String maturityRating) {
        this.maturityRating = maturityRating;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getBackdropUrl() {
        return backdropUrl;
    }

    public void setBackdropUrl(String backdropUrl) {
        this.backdropUrl = backdropUrl;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    public String getMediaAssetUrl() {
        return mediaAssetUrl;
    }

    public void setMediaAssetUrl(String mediaAssetUrl) {
        this.mediaAssetUrl = mediaAssetUrl;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public void setStatus(ContentStatus status) {
        this.status = status;
    }

    public Set<String> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(Set<String> genreIds) {
        this.genreIds = genreIds;
    }
}
