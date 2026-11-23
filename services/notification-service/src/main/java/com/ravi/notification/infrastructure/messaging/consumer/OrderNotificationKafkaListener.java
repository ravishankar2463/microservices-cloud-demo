package com.ravi.notification.infrastructure.messaging.consumer;

import com.ravi.notification.application.service.DeadLetterQueueService;
import com.ravi.notification.application.service.InvoiceArchivalService;
import com.ravi.notification.infrastructure.messaging.inbox.IdempotentMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderNotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationKafkaListener.class);

    private final IdempotentMessageStore idempotencyStore;
    private final InvoiceArchivalService invoiceService;
    private final DeadLetterQueueService dlqService;
    private final ObjectMapper objectMapper;

    public OrderNotificationKafkaListener(IdempotentMessageStore idempotencyStore,
                                          InvoiceArchivalService invoiceService,
                                          DeadLetterQueueService dlqService,
                                          ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.invoiceService = invoiceService;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "notification-service-group",
            autoStartup = "${notification.kafka.listener.auto-startup:true}"
    )
    public void onOrderEvent(String messagePayload) {
        String eventId = "UNKNOWN";
        try {
            JsonNode root = objectMapper.readTree(messagePayload);
            eventId = root.path("eventId").asText("EVT-" + System.currentTimeMillis());
            String orderId = root.path("orderId").asText();
            String customerId = root.path("customerId").asText();
            String eventType = root.path("eventType").asText("");
            String status = root.path("status").asText("");

            boolean isConfirmed = "ORDER_CONFIRMED".equalsIgnoreCase(eventType)
                    || eventType.contains("Confirm")
                    || "CONFIRMED".equalsIgnoreCase(status);

            if (!isConfirmed) {
                log.debug("Notification Service skipping non-confirmed event [type={}, status={}] for order: {}", eventType, status, orderId);
                return;
            }

            log.info("Notification Service received ORDER_CONFIRMED event [{}] for order: {}", eventId, orderId);

            // 1. Enforce Idempotency Guard
            if (!idempotencyStore.processIfNotSeen(eventId)) {
                log.info("Duplicate order notification event {} bypassed.", eventId);
                return;
            }

            // 2. Generate and Archive Invoice Document into AWS S3
            String invoiceContent = """
                    {
                      "invoiceId": "INV-%s",
                      "orderId": "%s",
                      "customerId": "%s",
                      "status": "GENERATED",
                      "source": "Kafka:order-events"
                    }
                    """.formatted(orderId, orderId, customerId);

            invoiceService.archiveInvoice(orderId, invoiceContent);
            log.info("Generated and archived invoice for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to process order event in notification-service: {}", e.getMessage());
            dlqService.parkMessage(eventId, "order-events", messagePayload, e.getMessage());
        }
    }
}
