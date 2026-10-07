package com.streamx.trending.dto;

import java.util.UUID;

public class RecordEventRequestDto {
    private UUID contentId;
    private String title;
    private String contentType;
    private String eventType; // VIEW, COMPLETION, LIKE

    public RecordEventRequestDto() {
    }

    public RecordEventRequestDto(UUID contentId, String title, String contentType, String eventType) {
        this.contentId = contentId;
        this.title = title;
        this.contentType = contentType;
        this.eventType = eventType;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}
