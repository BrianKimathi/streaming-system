package com.streamx.watchhistory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RecordProgressRequest {

    @NotBlank(message = "Content ID is required")
    private String contentId;

    /** Movie or show id; defaults to contentId for older clients. */
    private String titleId;

    /** MOVIE or SERIES; defaults to MOVIE for older clients. */
    private String titleType;

    @NotNull(message = "Position in seconds is required")
    @Min(value = 0, message = "Position cannot be negative")
    private Long positionSeconds;

    @NotNull(message = "Duration in seconds is required")
    @Min(value = 1, message = "Duration must be at least 1 second")
    private Long durationSeconds;

    public RecordProgressRequest() {
    }

    public RecordProgressRequest(String contentId, String titleId, String titleType,
                                 Long positionSeconds, Long durationSeconds) {
        this.contentId = contentId;
        this.titleId = titleId;
        this.titleType = titleType;
        this.positionSeconds = positionSeconds;
        this.durationSeconds = durationSeconds;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getTitleId() {
        return titleId;
    }

    public void setTitleId(String titleId) {
        this.titleId = titleId;
    }

    public String getTitleType() {
        return titleType;
    }

    public void setTitleType(String titleType) {
        this.titleType = titleType;
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
