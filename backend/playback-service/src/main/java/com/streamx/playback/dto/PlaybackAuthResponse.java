package com.streamx.playback.dto;

import com.streamx.playback.domain.PlaybackStatus;

import java.time.LocalDateTime;

public class PlaybackAuthResponse {
    private String sessionId;
    private String contentId;
    private String streamUrl;
    private PlaybackStatus status;
    private LocalDateTime expiresAt;

    public PlaybackAuthResponse() {
    }

    public PlaybackAuthResponse(String sessionId, String contentId, String streamUrl,
                                PlaybackStatus status, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.contentId = contentId;
        this.streamUrl = streamUrl;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public PlaybackStatus getStatus() {
        return status;
    }

    public void setStatus(PlaybackStatus status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
