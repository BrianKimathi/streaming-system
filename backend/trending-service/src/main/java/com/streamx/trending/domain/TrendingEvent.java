package com.streamx.trending.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trending_events", indexes = {
        @Index(name = "idx_trending_events_title_time", columnList = "title_id, occurred_at"),
        @Index(name = "idx_trending_events_time", columnList = "occurred_at")
})
public class TrendingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title_id", nullable = false)
    private UUID titleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private TrendingEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public TrendingEvent() {
    }

    public TrendingEvent(UUID titleId, TrendingEventType eventType, Instant occurredAt) {
        this.titleId = titleId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTitleId() {
        return titleId;
    }

    public void setTitleId(UUID titleId) {
        this.titleId = titleId;
    }

    public TrendingEventType getEventType() {
        return eventType;
    }

    public void setEventType(TrendingEventType eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
