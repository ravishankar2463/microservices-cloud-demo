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
    private static final int MAX_EVENTS = 200;

    private static final Pattern CARD_PATTERN = Pattern.compile("(?i)(\"(?:cardNumber|card_number|pan)\"\\s*:\\s*)[0-9\"-]+");
    private static final Pattern CVV_PATTERN = Pattern.compile("(?i)(\"(?:cvv|cvc)\"\\s*:\\s*)[0-9\"A-Za-z]+");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(\"(?:password|secret|token|apiKey|api_key)\"\\s*:\\s*\")[^\"]+(\")");

    public KafkaEventExplorerService() {
        // Starts completely clean: populated only when real events are emitted/consumed
    }

    public List<EventRecord> getEvents(String topic, String status, int limit) {
        return eventHistory.stream()
            .filter(e -> topic == null || topic.isBlank() || e.topic().equalsIgnoreCase(topic))
            .filter(e -> status == null || status.isBlank() || e.status().equalsIgnoreCase(status))
            .sorted(Comparator.comparing(EventRecord::timestamp).reversed())
            .limit(limit > 0 ? limit : 50)
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
            event.timestamp() != null ? event.timestamp() : Instant.now()
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

    private String maskSensitiveData(String payload) {
        if (payload == null) return "{}";
        String masked = CARD_PATTERN.matcher(payload).replaceAll("$1\"****-****-****-XXXX\"");
        masked = CVV_PATTERN.matcher(masked).replaceAll("$1\"***\"");
        return SECRET_PATTERN.matcher(masked).replaceAll("$1***$2");
    }
}
