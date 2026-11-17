package com.ravi.notification.infrastructure.messaging.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotent Consumer filter to protect against at-least-once message delivery duplication
 * from Kafka and AWS SQS.
 */
@Component
public class IdempotentMessageStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotentMessageStore.class);
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    /**
     * Atomically marks an event as processed if not already seen.
     *
     * @param eventId Unique identifier of the message
     * @return true if this is the first time seeing the message (proceed); false if duplicate (skip).
     */
    public boolean processIfNotSeen(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }

        boolean firstTime = processedMessageIds.add(eventId);
        if (!firstTime) {
            log.warn("Idempotency: Duplicate event detected ({}), safely skipping duplicate processing.", eventId);
        }
        return firstTime;
    }

    public boolean isProcessed(String eventId) {
        return processedMessageIds.contains(eventId);
    }
}
