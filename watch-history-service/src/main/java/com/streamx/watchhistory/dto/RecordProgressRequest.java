package com.streamx.watchhistory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RecordProgressRequest {

    @NotBlank(message = "Content ID is required")
    private String contentId;

    private String episodeId;

    @NotNull(message = "Position in seconds is required")
    @Min(value = 0)
    private Long positionSeconds;

    @NotNull(message = "Duration in seconds is required")
    @Min(value = 1)
    private Long durationSeconds;

    public RecordProgressRequest() {
    }

    public RecordProgressRequest(String contentId, String episodeId, Long positionSeconds, Long durationSeconds) {
        this.contentId = contentId;
        this.episodeId = episodeId;
        this.positionSeconds = positionSeconds;
        this.durationSeconds = durationSeconds;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
        this.episodeId = episodeId;
    }

    public Long getPositionSeconds() {
        return positionSeconds;
    }

    public void setPositionSeconds(Long positionSeconds) {
        this.positionSeconds = positionSeconds;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
