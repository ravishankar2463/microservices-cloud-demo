package com.ravi.notification.application.service;

import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dead Letter Queue (DLQ) Parking & Redrive Management Service.
 * Buffers poisoned or failed asynchronous Kafka/SQS events for operational triage and replay.
 */
@Service
public class DeadLetterQueueService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);

    @Schema(description = "Poisoned or unprocessable event record parked in the Dead Letter Queue")
    public record DeadLetterMessage(
            @Schema(description = "Unique message identifier", example = "msg-dlq-001")
            String id,

            @Schema(description = "Originating event topic name", example = "order-events")
            String topic,

            @Schema(description = "Serialized message payload", example = "{\"orderId\":\"3fa85f64\",\"customerId\":\"cust_84920\"}")
            String payload,

            @Schema(description = "Failure cause or exception reason", example = "S3 Connection refused during invoice archiving")
            String reason,

            @Schema(description = "Timestamp when message was parked in DLQ", example = "2026-11-23T15:30:10Z")
            Instant failedAt,

            @Schema(description = "Number of retry attempts executed", example = "3")
            int retryCount
    ) {}

    private final Map<String, DeadLetterMessage> dlqStore = new ConcurrentHashMap<>();

    public void parkMessage(String id, String topic, String payload, String reason) {
        log.warn("Parking unprocessable message in DLQ: id={}, topic={}, reason={}", id, topic, reason);
        dlqStore.put(id, new DeadLetterMessage(id, topic, payload, reason, Instant.now(), 1));
    }

    public List<DeadLetterMessage> getAllMessages() {
        return List.copyOf(dlqStore.values());
    }

    public Optional<DeadLetterMessage> getMessage(String id) {
        return Optional.ofNullable(dlqStore.get(id));
    }

    public boolean removeMessage(String id) {
        return dlqStore.remove(id) != null;
    }

    public int clearAll() {
        int size = dlqStore.size();
        dlqStore.clear();
        return size;
    }
}
