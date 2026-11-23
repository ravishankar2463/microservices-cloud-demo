package com.ravi.controlcenter.model;

import java.time.Instant;

public record EventRecord(
    String eventId,
    String eventType,
    String topic,
    String producer,
    String consumer,
    String status,        // PUBLISHED, CONSUMED, FAILED
    String maskedPayload,
    String correlationId,
    Instant timestamp,
    int partition,
    long offset
) {
    public EventRecord(
            String eventId,
            String eventType,
            String topic,
            String producer,
            String consumer,
            String status,
            String maskedPayload,
            String correlationId,
            Instant timestamp) {
        this(eventId, eventType, topic, producer, consumer, status, maskedPayload, correlationId, timestamp, 0, -1L);
    }
}
