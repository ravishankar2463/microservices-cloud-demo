package com.ravi.inventory.infrastructure.messaging.consumer;

import com.ravi.inventory.application.service.InventoryReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryReservationService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(InventoryReservationService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "inventory-service-group",
            autoStartup = "${inventory.kafka.listener.auto-startup:true}"
    )
    public void onOrderEvent(String messagePayload) {
        try {
            JsonNode root = objectMapper.readTree(messagePayload);
            String orderId = root.path("orderId").asText();
            String eventType = root.path("eventType").asText("");
            String status = root.path("status").asText("");
            boolean isCancellation = eventType.contains("Cancel") || eventType.contains("CANCEL") || root.has("reason") || "CANCELLED".equalsIgnoreCase(status);
            boolean isConfirmation = eventType.contains("Confirm") || eventType.contains("CONFIRM") || "CONFIRMED".equalsIgnoreCase(status);
            JsonNode items = root.path("items");

            if (isConfirmation) {
                log.info("Received OrderConfirmedEvent for order: {}. Stock already reserved; no inventory change needed.", orderId);
                return;
            }

            if (isCancellation) {
                log.info("Received Order Cancellation Event for order: {}. Executing Saga stock compensation...", orderId);
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String productId = item.path("productId").asText();
                        int quantity = item.path("quantity").asInt(1);
                        boolean released = inventoryService.releaseStock(productId, quantity);
                        log.info("Saga compensation release for product {}: {}", productId, released ? "SUCCESS" : "FAILED");
                    }
                }
            } else {
                log.info("Received Order Event from Kafka for order: {}. Auto-reserving stock...", orderId);
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String productId = item.path("productId").asText();
                        int quantity = item.path("quantity").asInt(1);
                        boolean reserved = inventoryService.reserveStock(productId, quantity);
                        log.info("Auto-reservation for product {}: {}", productId, reserved ? "SUCCESS" : "OUT_OF_STOCK");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka order event in inventory-service: {}", e.getMessage());
        }
    }
}
