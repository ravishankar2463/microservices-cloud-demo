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
    Instant timestamp
) {
}
