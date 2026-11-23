package com.ravi.inventory.infrastructure.messaging.producer;

import com.ravi.inventory.domain.event.InventoryCommittedEvent;
import com.ravi.inventory.domain.event.InventoryRejectedEvent;
import com.ravi.inventory.domain.event.InventoryReleasedEvent;
import com.ravi.inventory.domain.event.InventoryReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);
    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishInventoryReserved(InventoryReservedEvent event) {
        sendEvent(event.orderId(), event.eventType(), event);
    }

    public void publishInventoryRejected(InventoryRejectedEvent event) {
        sendEvent(event.orderId(), event.eventType(), event);
    }

    public void publishInventoryReleased(InventoryReleasedEvent event) {
        sendEvent(event.orderId(), event.eventType(), event);
    }

    public void publishInventoryCommitted(InventoryCommittedEvent event) {
        sendEvent(event.orderId(), event.eventType(), event);
    }

    private void sendEvent(String orderId, String eventType, Object event) {
        log.info("Emitting {} for order {}", eventType, orderId);
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available; skipping {} publish for order {}", eventType, orderId);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, orderId, payload);
            log.info("Published {} to topic {}: {}", eventType, INVENTORY_EVENTS_TOPIC, payload);
        } catch (Exception e) {
            log.error("Failed to publish {} to topic {} for order {}: {}",
                    eventType, INVENTORY_EVENTS_TOPIC, orderId, e.getMessage());
        }
    }
}
