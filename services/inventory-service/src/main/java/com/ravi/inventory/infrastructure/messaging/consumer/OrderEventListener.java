package com.ravi.inventory.infrastructure.messaging.consumer;

import com.ravi.inventory.application.service.InventoryReservationService;
import com.ravi.inventory.domain.event.InventoryCommittedEvent;
import com.ravi.inventory.domain.event.InventoryRejectedEvent;
import com.ravi.inventory.domain.event.InventoryReleasedEvent;
import com.ravi.inventory.domain.event.InventoryReservedEvent;
import com.ravi.inventory.domain.model.StockOperationResult;
import com.ravi.inventory.infrastructure.messaging.producer.InventoryEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryReservationService inventoryService;
    private final InventoryEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public OrderEventListener(
            InventoryReservationService inventoryService,
            InventoryEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
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
            String customerId = root.path("customerId").asText("guest-customer");
            String eventType = root.path("eventType").asText("");
            String status = root.path("status").asText("");
            boolean isCancellation = eventType.contains("Cancel") || eventType.contains("CANCEL") || root.has("reason") || "CANCELLED".equalsIgnoreCase(status);
            boolean isConfirmation = eventType.contains("Confirm") || eventType.contains("CONFIRM") || "CONFIRMED".equalsIgnoreCase(status);
            boolean isCompletion = eventType.contains("Complete") || eventType.contains("COMPLETE") || "COMPLETED".equalsIgnoreCase(status);
            JsonNode items = root.path("items");

            if (isCompletion) {
                log.info("Received OrderCompletedEvent for order: {}. Reserved stock already committed for fulfillment.", orderId);
                return;
            }

            if (isConfirmation) {
                log.info("Received OrderConfirmedEvent for order: {}. Committing reserved stock for dispatch...", orderId);
                List<StockOperationResult> committedResults = inventoryService.commitForOrder(orderId);
                if (committedResults.isEmpty()) {
                    log.info("No pending stock reservations found to commit for order: {} (already committed or untracked).", orderId);
                    return;
                }
                for (StockOperationResult committed : committedResults) {
                    eventPublisher.publishInventoryCommitted(new InventoryCommittedEvent(
                            UUID.randomUUID(),
                            orderId,
                            committed.productId(),
                            committed.affectedQuantity(),
                            committed.availableBefore(),
                            committed.availableAfter(),
                            committed.reservedBefore(),
                            committed.reservedAfter(),
                            Instant.now()
                    ));
                }
                return;
            }

            if (isCancellation) {
                log.info("Received Order Cancellation Event for order: {}. Evaluating Saga stock compensation...", orderId);
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String productId = item.path("productId").asText();
                        int quantity = item.path("quantity").asInt(1);
                        StockOperationResult result = inventoryService.releaseForOrder(orderId, productId, quantity);
                        if (result.success() && result.affectedQuantity() > 0) {
                            log.info("Saga compensation release for order {} product {}: SUCCESS ({} -> {})",
                                    orderId, productId, result.availableBefore(), result.availableAfter());
                            eventPublisher.publishInventoryReleased(new InventoryReleasedEvent(
                                    UUID.randomUUID(),
                                    orderId,
                                    productId,
                                    result.affectedQuantity(),
                                    result.availableBefore(),
                                    result.availableAfter(),
                                    result.reservedBefore(),
                                    result.reservedAfter(),
                                    Instant.now()
                            ));
                        } else {
                            log.info("Saga compensation release skipped for order {} product {}: {}",
                                    orderId, productId, result.reason());
                        }
                    }
                }
            } else {
                log.info("Received Order Event from Kafka for order: {}. Evaluating stock reservation...", orderId);
                if (items.isArray()) {
                    List<StockOperationResult> succeeded = new ArrayList<>();
                    for (JsonNode item : items) {
                        String productId = item.path("productId").asText();
                        int quantity = item.path("quantity").asInt(1);
                        StockOperationResult result = inventoryService.reserveForOrder(orderId, productId, quantity);
                        if (result.success()) {
                            succeeded.add(result);
                            log.info("Auto-reservation for order {} product {}: SUCCESS ({} -> {})",
                                    orderId, productId, result.availableBefore(), result.availableAfter());
                        } else {
                            log.warn("Auto-reservation for order {} product {}: REJECTED ({})",
                                    orderId, productId, result.reason());
                            // Roll back any prior items already reserved for this multi-item order
                            for (StockOperationResult prev : succeeded) {
                                inventoryService.releaseForOrder(orderId, prev.productId(), prev.affectedQuantity());
                            }
                            eventPublisher.publishInventoryRejected(new InventoryRejectedEvent(
                                    UUID.randomUUID(),
                                    orderId,
                                    customerId,
                                    productId,
                                    quantity,
                                    result.availableAfter(),
                                    result.reservedAfter(),
                                    result.reason(),
                                    Instant.now()
                            ));
                            return;
                        }
                    }

                    for (StockOperationResult reserved : succeeded) {
                        eventPublisher.publishInventoryReserved(new InventoryReservedEvent(
                                UUID.randomUUID(),
                                orderId,
                                customerId,
                                reserved.productId(),
                                reserved.requestedQuantity(),
                                reserved.availableBefore(),
                                reserved.availableAfter(),
                                reserved.reservedBefore(),
                                reserved.reservedAfter(),
                                Instant.now()
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka order event in inventory-service: {}", e.getMessage());
        }
    }
}
