package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;

import java.time.LocalDate;
import java.util.Set;

public class MovieResponse {
    private String id;
    private String title;
    private String synopsis;
    private LocalDate releaseDate;
    private Integer runtimeMinutes;
    private String maturityRating;
    private String posterUrl;
    private String backdropUrl;
    private String trailerUrl;
    private String mediaAssetUrl;
    private ContentStatus status;
    private Set<GenreResponse> genres;

    public MovieResponse() {
    }

    public MovieResponse(String id, String title, String synopsis, LocalDate releaseDate, Integer runtimeMinutes,
                         String maturityRating, String posterUrl, String backdropUrl, String trailerUrl,
                         String mediaAssetUrl, ContentStatus status, Set<GenreResponse> genres) {
        this.id = id;
        this.title = title;
        this.synopsis = synopsis;
        this.releaseDate = releaseDate;
        this.runtimeMinutes = runtimeMinutes;
        this.maturityRating = maturityRating;
        this.posterUrl = posterUrl;
        this.backdropUrl = backdropUrl;
        this.trailerUrl = trailerUrl;
        this.mediaAssetUrl = mediaAssetUrl;
        this.status = status;
        this.genres = genres;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Set<GenreResponse> getGenres() {
        return genres;
    }

    public void setGenres(Set<GenreResponse> genres) {
        this.genres = genres;
    }
}
