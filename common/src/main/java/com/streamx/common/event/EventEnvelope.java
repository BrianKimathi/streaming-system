package com.streamx.common.event;

import java.time.LocalDateTime;

public class EventEnvelope<T> {
    private String eventId;
    private EventType eventType;
    private String eventVersion;
    private LocalDateTime occurredAt;
    private String producer;
    private String correlationId;
    private String aggregateId;
    private T payload;

    public EventEnvelope() {
    }

    public EventEnvelope(String eventId, EventType eventType, String eventVersion, LocalDateTime occurredAt,
                         String producer, String correlationId, String aggregateId, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.occurredAt = occurredAt;
        this.producer = producer;
        this.correlationId = correlationId;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(String eventVersion) {
        this.eventVersion = eventVersion;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}
