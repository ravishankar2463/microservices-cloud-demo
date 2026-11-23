package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.EventRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

@Service
public class KafkaEventExplorerService {

    private final Deque<EventRecord> eventHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_EVENTS = 500;

    private static final Pattern CARD_PATTERN = Pattern.compile("(?i)(\"(?:cardNumber|card_number|pan)\"\\s*:\\s*)[0-9\"-]+");
    private static final Pattern CVV_PATTERN = Pattern.compile("(?i)(\"(?:cvv|cvc)\"\\s*:\\s*)[0-9\"A-Za-z]+");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(\"(?:password|secret|token|apiKey|api_key)\"\\s*:\\s*\")[^\"]+(\")");

    public KafkaEventExplorerService() {
        // Starts completely clean: populated only when real events are emitted/consumed
    }

    public List<EventRecord> getEvents(String topic, String status, int limit) {
        return getEvents(topic, status, null, limit);
    }

    public List<EventRecord> getEvents(String topic, String status, String query, int limit) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : null;
        return eventHistory.stream()
            .filter(e -> topic == null || topic.isBlank() || e.topic().equalsIgnoreCase(topic))
            .filter(e -> status == null || status.isBlank() || e.status().equalsIgnoreCase(status))
            .filter(e -> normalizedQuery == null || matchesQuery(e, normalizedQuery))
            .sorted(Comparator.comparing(EventRecord::timestamp).reversed())
            .limit(limit > 0 ? limit : 100)
            .toList();
    }

    public void recordEvent(EventRecord event) {
        String safePayload = maskSensitiveData(event.maskedPayload());
        EventRecord sanitized = new EventRecord(
            event.eventId(),
            event.eventType(),
            event.topic(),
            event.producer(),
            event.consumer(),
            event.status(),
            safePayload,
            event.correlationId(),
            event.timestamp() != null ? event.timestamp() : Instant.now(),
            event.partition(),
            event.offset()
        );
        eventHistory.addFirst(sanitized);
        while (eventHistory.size() > MAX_EVENTS) {
            eventHistory.pollLast();
        }
    }

    public void clearAll() {
        eventHistory.clear();
    }

    public List<String> getTopics() {
        return List.of(
            "order-events",
            "payment-events",
            "inventory-events",
            "order-events-dlq"
        );
    }

    private boolean matchesQuery(EventRecord e, String q) {
        if (containsIgnoreCase(e.correlationId(), q)) return true;
        if (containsIgnoreCase(e.eventId(), q)) return true;
        if (containsIgnoreCase(e.eventType(), q)) return true;
        if (containsIgnoreCase(e.topic(), q)) return true;
        if (containsIgnoreCase(e.producer(), q)) return true;
        if (containsIgnoreCase(e.consumer(), q)) return true;
        if (containsIgnoreCase(e.maskedPayload(), q)) return true;
        if (e.offset() >= 0) {
            String offsetStr = String.valueOf(e.offset());
            if (offsetStr.equals(q) || ("offset " + offsetStr).contains(q) || ("#" + offsetStr).equals(q)) {
                return true;
            }
            String partOffset = "p" + e.partition() + ":" + offsetStr;
            if (partOffset.contains(q) || ("partition " + e.partition()).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private String maskSensitiveData(String payload) {
        if (payload == null) return "{}";
        String masked = CARD_PATTERN.matcher(payload).replaceAll("$1\"****-****-****-XXXX\"");
        masked = CVV_PATTERN.matcher(masked).replaceAll("$1\"***\"");
        return SECRET_PATTERN.matcher(masked).replaceAll("$1***$2");
    }
}
