package com.ravi.order.saga.orchestration;

import com.ravi.order.application.command.CancelOrderCommand;
import com.ravi.order.application.command.handler.OrderCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Saga Orchestrator coordinating cross-service distributed workflows.
 * Listens for payment callback events on Kafka and executes confirmation or compensation.
 */
@Component
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderCommandHandler orderCommandHandler;
    private final ObjectMapper objectMapper;

    public OrderSagaOrchestrator(OrderCommandHandler orderCommandHandler, ObjectMapper objectMapper) {
        this.orderCommandHandler = orderCommandHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "payment-events",
            groupId = "order-saga-orchestrator-group",
            autoStartup = "${order.saga.kafka.auto-startup:true}"
    )
    public void onPaymentEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String orderIdStr = root.path("orderId").asText();
            if (orderIdStr.isBlank()) {
                log.warn("Received payment event without valid orderId: {}", payload);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);

            if (root.hasNonNull("failureReason")) {
                String reason = root.path("failureReason").asText();
                log.warn("SAGA ORCHESTRATION: Payment FAILED for order {}. Initiating compensating cancellation...", orderId);
                orderCommandHandler.handle(new CancelOrderCommand(orderId, "Payment failed: " + reason));
            } else if (root.hasNonNull("transactionReference")) {
                String txn = root.path("transactionReference").asText();
                log.info("SAGA ORCHESTRATION: Payment CAPTURED [{}] for order {}. Confirming order...", txn, orderId);
                orderCommandHandler.confirmOrder(orderId);
            }
        } catch (Exception e) {
            log.error("Error processing payment event in OrderSagaOrchestrator: {}", e.getMessage(), e);
        }
    }
}
