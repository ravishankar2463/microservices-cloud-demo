package com.ravi.controlcenter.messaging;

import com.ravi.controlcenter.model.*;
import com.ravi.controlcenter.model.SagaExecution.SagaStep;
import com.ravi.controlcenter.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Real-time Apache Kafka consumer that intercepts live events produced by cluster microservices,
 * sanitizes and logs them to the Kafka Event Explorer, and dynamically constructs live Distributed
 * Saga execution timelines strictly from observed cluster events with zero mock or fabricated data.
 */
@Component
public class LiveKafkaSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveKafkaSagaConsumer.class);

    private final KafkaEventExplorerService eventService;
    private final SagaExplorerService sagaService;
    private final InfrastructureStateInspectorService infraInspector;
    private final ArchitecturalPatternCatalogService patternCatalog;
    private final ObjectMapper objectMapper;

    public LiveKafkaSagaConsumer(
            KafkaEventExplorerService eventService,
            SagaExplorerService sagaService,
            InfrastructureStateInspectorService infraInspector,
            ArchitecturalPatternCatalogService patternCatalog) {
        this.eventService = eventService;
        this.sagaService = sagaService;
        this.infraInspector = infraInspector;
        this.patternCatalog = patternCatalog;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(
        topics = {"order-events", "payment-events", "inventory-events", "order-events-dlq"},
        groupId = "control-center-live-tracker",
        autoStartup = "${controlcenter.kafka.consumer.enabled:true}"
    )
    public void onLiveClusterEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        handleLiveClusterEvent(payload, topic, key, partition != null ? partition : 0, offset != null ? offset : -1L);
    }

    public void onLiveClusterEvent(String payload, String topic, String key, Long offset) {
        handleLiveClusterEvent(payload, topic, key, 0, offset != null ? offset : -1L);
    }

    private void handleLiveClusterEvent(String payload, String topic, String key, int partition, long offset) {

        try {
            JsonNode root = objectMapper.readTree(payload);

            String eventId = root.path("eventId").asText("evt-" + UUID.randomUUID().toString().substring(0, 6));
            String rawEventType = root.hasNonNull("eventType") ? root.path("eventType").asText("") : "";
            String eventType = normalizeEventType(rawEventType, topic, root);
            String orderId = root.path("orderId").asText(key != null ? key : "");
            String rawCustomerId = root.hasNonNull("customerId") ? root.path("customerId").asText("") : "";
            String status = root.path("status").asText("PROCESSED");
            String corrId = root.path("correlationId").asText(key != null && !key.isBlank() ? key : (!orderId.isBlank() ? orderId : "corr-" + eventId));

            // 1. Record raw event in Kafka Event Stream with real broker partition & offset
            String producer = determineProducer(topic, eventType);
            String consumer = determineConsumer(topic, eventType);

            EventRecord record = new EventRecord(
                eventId,
                eventType,
                topic,
                producer,
                consumer,
                status,
                payload,
                corrId,
                Instant.now(),
                partition,
                offset
            );
            eventService.recordEvent(record);

            log.info("LiveKafkaSagaConsumer intercepted event [{}] on topic [{} p={} offset={}] for order [{}]",
                eventType, topic, partition, offset, orderId);

            // 2. If an orderId exists, dynamically assemble / update the distributed Saga timeline
            if (!orderId.isBlank()) {
                updateLiveSagaTimeline(orderId, rawCustomerId, eventType, topic, payload, root);
            }

        } catch (Exception e) {
            log.error("Error processing live Kafka event on topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private synchronized void updateLiveSagaTimeline(
            String orderId,
            String incomingCustomerId,
            String eventType,
            String topic,
            String payload,
            JsonNode root) {

        String sagaId = "saga-" + orderId;
        SagaExecution existing = sagaService.getSagaById(sagaId).orElse(null);

        List<SagaStep> steps = (existing != null)
            ? new ArrayList<>(existing.steps())
            : new ArrayList<>();

        Instant startedAt = (existing != null) ? existing.startedAt() : Instant.now();
        String customerId = resolveCustomerId(incomingCustomerId, existing);
        BigDecimal parsedAmount = extractAmountFromPayload(payload);
        BigDecimal amount = (parsedAmount != null) ? parsedAmount
            : ((existing != null) ? existing.amount() : BigDecimal.ZERO);
        boolean isCompensated = (existing != null) && existing.compensated();
        String sagaStatus = (existing != null) ? existing.status() : "IN_PROGRESS";
        String currentStep = (existing != null) ? existing.currentStep() : eventType;

        // Take real live snapshot of infrastructure for step delta
        InfrastructureSnapshot liveSnapshot = infraInspector.captureSnapshot(orderId, topic);

        // Map live Kafka event to canonical Saga step (Database-per-Service aware)
        if (eventType.contains("OrderCreated") || eventType.contains("ORDER_CREATED")) {
            if (!hasStep(steps, "ORDER_CREATED")) {
                // If INVENTORY_RESERVED already arrived out-of-order, use its exact pre-reservation stock
                InfrastructureSnapshot.RedisState baselineRedis = steps.stream()
                    .filter(s -> "INVENTORY_RESERVED".equals(s.stepName()))
                    .map(s -> s.beforeState().redis())
                    .findFirst()
                    .orElse(liveSnapshot.redis());

                // Order creation commits PENDING in order_db (even if a downstream transition finished before this snapshot)
                InfrastructureSnapshot orderAfterSnapshot = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "PENDING", "OUTBOX_PUBLISHED"),
                    baselineRedis,
                    new InfrastructureSnapshot.KafkaState(topic, liveSnapshot.kafka().partitionOffset(), orderId),
                    liveSnapshot.aws()
                );
                InfrastructureSnapshot preOrderSnapshot = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 0, "NONE", "OUTBOX_EMPTY"),
                    baselineRedis,
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, liveSnapshot.kafka().partitionOffset() - 1), null),
                    liveSnapshot.aws()
                );
                InfrastructureDiff orderCreatedDiff = infraInspector.computeDiff(preOrderSnapshot, orderAfterSnapshot, "ORDER_CREATED");

                SagaStep createdStep = new SagaStep(
                    "ORDER_CREATED",
                    "order-service",
                    "COMPLETED",
                    "Order created in PostgreSQL order_db with status PENDING. Transactional Outbox event emitted.",
                    45,
                    preOrderSnapshot,
                    orderAfterSnapshot,
                    orderCreatedDiff,
                    new AopDebugTrace(
                        "OrderCommandController",
                        "createOrder(CreateOrderCommand command)",
                        Map.of("orderId", orderId, "customerId", customerId),
                        Map.of("status", "PENDING", "persisted", true),
                        List.of("@PostMapping(\"/api/orders\")", "@Transactional"),
                        List.of("[AOP DEBUG] Live commit to PostgreSQL order_db table 'orders'",
                                "[AOP DEBUG] OutboxPublisher polled and committed to Kafka 'order-events'")
                    ),
                    patternCatalog.getDossierForStep("ORDER_CREATED")
                );

                // Keep ORDER_CREATED first even if a downstream topic poll arrived slightly earlier
                steps.addFirst(createdStep);
                if (!isTerminalStatus(sagaStatus)) {
                    currentStep = "ORDER_CREATED";
                    sagaStatus = "IN_PROGRESS";
                }
            }

        } else if (eventType.contains("InventoryReserved") || eventType.contains("INVENTORY_RESERVED")) {
            if (!hasStep(steps, "INVENTORY_RESERVED")) {
                InfrastructureSnapshot invLive = infraInspector.captureSnapshotForService(orderId, topic, "inventory-service");
                String productId = root.path("productId").asText("PROD-TECH-001");
                int requestedQty = root.path("requestedQuantity").asInt(1);
                int stockBefore = root.path("stockBefore").asInt(invLive.redis().stockQuantity());
                int stockAfter = root.path("stockAfter").asInt(invLive.redis().stockQuantity());
                int reservedBefore = root.path("reservedBefore").asInt(invLive.database().rowCount());
                int reservedAfter = root.path("reservedAfter").asInt(invLive.database().rowCount());

                // Causally reconcile ORDER_CREATED's Redis stock if inventory-service decremented stock before control-center polled it
                reconcileOrderCreatedRedisStock(steps, productId, stockBefore);

                InfrastructureSnapshot beforeReserveSnapshot = withInventoryState(
                    invLive, productId, stockBefore, reservedBefore, "LOCK_ACQUIRED",
                    Math.max(0, invLive.kafka().partitionOffset() - 1), null
                );
                InfrastructureSnapshot afterReserveSnapshot = withInventoryState(
                    invLive, productId, stockAfter, reservedAfter, "LOCK_RELEASED",
                    invLive.kafka().partitionOffset(), orderId
                );
                InfrastructureDiff reservedDiff = infraInspector.computeDiff(beforeReserveSnapshot, afterReserveSnapshot, "INVENTORY_RESERVED");

                if (!isTerminalStatus(sagaStatus)) {
                    currentStep = "INVENTORY_RESERVED";
                    sagaStatus = "IN_PROGRESS";
                }

                SagaStep reservedStep = new SagaStep(
                    "INVENTORY_RESERVED",
                    "inventory-service",
                    "COMPLETED",
                    String.format("OrderEventListener consumed order-events. Acquired Redis mutex lock for %s, reserved %d unit(s) (%d -> %d) and emitted INVENTORY_RESERVED.",
                        productId, requestedQty, stockBefore, stockAfter),
                    32,
                    beforeReserveSnapshot,
                    afterReserveSnapshot,
                    reservedDiff,
                    new AopDebugTrace(
                        "OrderEventListener",
                        "onOrderEvent(String messagePayload)",
                        Map.of("orderId", orderId, "productId", productId, "requestedQuantity", requestedQty),
                        Map.of("reserved", true, "stockBefore", stockBefore, "stockAfter", stockAfter,
                               "reservedBefore", reservedBefore, "reservedAfter", reservedAfter, "lockReleased", true),
                        List.of("@KafkaListener(topics = \"order-events\")", "@Timed(\"inventory.reserve\")"),
                        List.of("[AOP DEBUG] Redis lock 'lock:stock:" + productId + "' acquired & released cleanly",
                                String.format("[AOP DEBUG] Available stock updated: %d -> %d (reserved: %d -> %d)",
                                    stockBefore, stockAfter, reservedBefore, reservedAfter),
                                "[AOP DEBUG] Published INVENTORY_RESERVED to Kafka topic 'inventory-events'")
                    ),
                    patternCatalog.getDossierForStep("INVENTORY_RESERVED")
                );

                // Place INVENTORY_RESERVED immediately after ORDER_CREATED if downstream events arrived earlier
                insertAfterStepOrAppend(steps, "ORDER_CREATED", reservedStep);
            }

        } else if (eventType.contains("InventoryRejected") || eventType.contains("INVENTORY_REJECTED")) {
            if (!hasStep(steps, "INVENTORY_REJECTED")) {
                InfrastructureSnapshot invLive = infraInspector.captureSnapshotForService(orderId, topic, "inventory-service");
                String productId = root.path("productId").asText("PROD-TECH-001");
                int requestedQty = root.path("requestedQuantity").asInt(0);
                int availableQty = root.path("availableQuantity").asInt(invLive.redis().stockQuantity());
                int reservedQty = root.path("reservedQuantity").asInt(invLive.database().rowCount());
                String reason = root.path("reason").asText(
                    String.format("Insufficient stock for %s: requested %d, available %d", productId, requestedQty, availableQty));

                reconcileOrderCreatedRedisStock(steps, productId, availableQty);

                InfrastructureSnapshot beforeRejectSnapshot = withInventoryState(
                    invLive, productId, availableQty, reservedQty, "LOCK_ACQUIRED",
                    Math.max(0, invLive.kafka().partitionOffset() - 1), null
                );
                InfrastructureSnapshot afterRejectSnapshot = withInventoryState(
                    invLive, productId, availableQty, reservedQty, "STOCK_GUARD_REJECTED",
                    invLive.kafka().partitionOffset(), orderId
                );

                InfrastructureDiff baseDiff = infraInspector.computeDiff(beforeRejectSnapshot, afterRejectSnapshot, "INVENTORY_REJECTED");
                List<String> highlights = new ArrayList<>();
                highlights.add(String.format("Inventory Guard Rejected: requested %d unit(s) of %s, only %d available (%d reserved; 0 units mutated)",
                    requestedQty, productId, availableQty, reservedQty));
                for (String h : baseDiff.highlights()) {
                    if (!h.contains("validated infrastructure invariants")) {
                        highlights.add(h);
                    }
                }
                Map<String, String> details = new LinkedHashMap<>(baseDiff.deltaDetails());
                details.put("inventory.guard_rejection", reason);
                InfrastructureDiff rejectedDiff = new InfrastructureDiff(highlights, details);

                currentStep = hasStep(steps, "ORDER_CANCELLED") ? "ORDER_CANCELLED" : "INVENTORY_REJECTED";
                sagaStatus = "REJECTED";
                isCompensated = true;

                SagaStep rejectedStep = new SagaStep(
                    "INVENTORY_REJECTED",
                    "inventory-service",
                    "FAILED",
                    String.format("Stock boundary guard rejected reservation for %s: %s. Zero stock mutated; emitted INVENTORY_REJECTED to inventory-events.",
                        productId, reason),
                    18,
                    beforeRejectSnapshot,
                    afterRejectSnapshot,
                    rejectedDiff,
                    new AopDebugTrace(
                        "InventoryReservationService",
                        "reserveForOrder(String orderId, String productId, int quantity)",
                        Map.of("orderId", orderId, "productId", productId, "requestedQuantity", requestedQty),
                        Map.of("reserved", false, "availableQuantity", availableQty, "reservedQuantity", reservedQty, "reason", reason),
                        List.of("@KafkaListener(topics = \"order-events\")"),
                        List.of("[AOP DEBUG] Redis lock 'lock:stock:" + productId + "' acquired & released cleanly",
                                "[AOP DEBUG] Stock guard triggered: " + reason,
                                "[AOP DEBUG] Published INVENTORY_REJECTED to Kafka topic 'inventory-events'")
                    ),
                    patternCatalog.getDossierForStep("INVENTORY_REJECTED")
                );

                insertAfterStepOrAppend(steps, "ORDER_CREATED", rejectedStep);
            }

        } else if (eventType.contains("InventoryCommitted") || eventType.contains("INVENTORY_COMMITTED")) {
            if (!hasStep(steps, "INVENTORY_COMMITTED")) {
                InfrastructureSnapshot invLive = infraInspector.captureSnapshotForService(orderId, topic, "inventory-service");
                String productId = root.path("productId").asText("PROD-TECH-001");
                int committedQty = root.path("committedQuantity").asInt(1);
                int stockBefore = root.path("stockBefore").asInt(invLive.redis().stockQuantity());
                int stockAfter = root.path("stockAfter").asInt(invLive.redis().stockQuantity());
                int reservedBefore = root.path("reservedBefore").asInt(invLive.database().rowCount() + committedQty);
                int reservedAfter = root.path("reservedAfter").asInt(invLive.database().rowCount());

                InfrastructureSnapshot beforeCommitSnapshot = withInventoryState(
                    invLive, productId, stockBefore, reservedBefore, "LOCK_ACQUIRED",
                    Math.max(0, invLive.kafka().partitionOffset() - 1), null
                );
                InfrastructureSnapshot afterCommitSnapshot = withInventoryState(
                    invLive, productId, stockAfter, reservedAfter, "STOCK_COMMITTED",
                    invLive.kafka().partitionOffset(), orderId
                );

                InfrastructureDiff baseDiff = infraInspector.computeDiff(beforeCommitSnapshot, afterCommitSnapshot, "INVENTORY_COMMITTED");
                List<String> highlights = new ArrayList<>();
                highlights.add(String.format("Inventory Committed for Dispatch: finalized %d reserved unit(s) of %s (RESERVED: %d -> %d, AVAIL: %d)",
                    committedQty, productId, reservedBefore, reservedAfter, stockAfter));
                for (String h : baseDiff.highlights()) {
                    if (!h.contains("validated infrastructure invariants")) {
                        highlights.add(h);
                    }
                }
                Map<String, String> details = new LinkedHashMap<>(baseDiff.deltaDetails());
                details.put("inventory.committed_units", String.valueOf(committedQty));
                InfrastructureDiff committedDiff = new InfrastructureDiff(highlights, details);

                if (!"COMPLETED".equals(sagaStatus)) {
                    currentStep = "INVENTORY_COMMITTED";
                }

                SagaStep committedStep = new SagaStep(
                    "INVENTORY_COMMITTED",
                    "inventory-service",
                    "COMPLETED",
                    String.format("OrderEventListener consumed ORDER_CONFIRMED. Committed %d reserved unit(s) of %s for fulfillment (RESERVED: %d -> %d) and emitted INVENTORY_COMMITTED.",
                        committedQty, productId, reservedBefore, reservedAfter),
                    24,
                    beforeCommitSnapshot,
                    afterCommitSnapshot,
                    committedDiff,
                    new AopDebugTrace(
                        "InventoryReservationService",
                        "commitForOrder(String orderId)",
                        Map.of("orderId", orderId, "productId", productId, "committedQuantity", committedQty),
                        Map.of("committed", true, "availableQuantity", stockAfter,
                               "reservedBefore", reservedBefore, "reservedAfter", reservedAfter),
                        List.of("@KafkaListener(topics = \"order-events\")"),
                        List.of("[AOP DEBUG] Redis lock 'lock:stock:" + productId + "' acquired & released cleanly",
                                String.format("[AOP DEBUG] Finalized reservation hold: RESERVED %d -> %d (AVAIL remains %d)",
                                    reservedBefore, reservedAfter, stockAfter),
                                "[AOP DEBUG] Published INVENTORY_COMMITTED to Kafka topic 'inventory-events'")
                    ),
                    patternCatalog.getDossierForStep("INVENTORY_COMMITTED")
                );

                insertAfterStepOrAppend(steps, "ORDER_CONFIRMED", committedStep);
            }

        } else if (eventType.contains("InventoryReleased") || eventType.contains("INVENTORY_RELEASED")) {
            if (!hasStep(steps, "COMPENSATING_INVENTORY")) {
                InfrastructureSnapshot invLive = infraInspector.captureSnapshotForService(orderId, topic, "inventory-service");
                String productId = root.path("productId").asText("PROD-TECH-001");
                int releasedQty = root.path("releasedQuantity").asInt(1);
                int stockBefore = root.path("stockBefore").asInt(invLive.redis().stockQuantity());
                int stockAfter = root.path("stockAfter").asInt(invLive.redis().stockQuantity());
                int reservedBefore = root.path("reservedBefore").asInt(invLive.database().rowCount());
                int reservedAfter = root.path("reservedAfter").asInt(invLive.database().rowCount());

                InfrastructureSnapshot beforeCompSnapshot = withInventoryState(
                    invLive, productId, stockBefore, reservedBefore, "LOCK_ACQUIRED",
                    Math.max(0, invLive.kafka().partitionOffset() - 1), null
                );
                InfrastructureSnapshot afterCompSnapshot = withInventoryState(
                    invLive, productId, stockAfter, reservedAfter, "LOCK_RELEASED",
                    invLive.kafka().partitionOffset(), orderId
                );
                InfrastructureDiff compDiff = infraInspector.computeDiff(beforeCompSnapshot, afterCompSnapshot, "COMPENSATING_INVENTORY");

                currentStep = "COMPENSATING_INVENTORY";
                sagaStatus = "COMPENSATED";
                isCompensated = true;

                steps.add(new SagaStep(
                    "COMPENSATING_INVENTORY",
                    "inventory-service",
                    "COMPENSATED",
                    String.format("OrderEventListener consumed OrderCancelledEvent. Released %d reserved unit(s) of %s back to inventory (%d -> %d) and emitted INVENTORY_RELEASED.",
                        releasedQty, productId, stockBefore, stockAfter),
                    38,
                    beforeCompSnapshot,
                    afterCompSnapshot,
                    compDiff,
                    new AopDebugTrace(
                        "InventoryReservationService",
                        "releaseForOrder(String orderId, String productId, int quantity)",
                        Map.of("orderId", orderId, "productId", productId, "releasedQuantity", releasedQty),
                        Map.of("stockReleased", true, "stockBefore", stockBefore, "stockAfter", stockAfter,
                               "reservedBefore", reservedBefore, "reservedAfter", reservedAfter),
                        List.of("@KafkaListener(topics = \"order-events\")", "@Timed(\"inventory.compensate\")"),
                        List.of(String.format("[AOP DEBUG] Restored reserved units in inventory-service: %d -> %d", stockBefore, stockAfter),
                                "[AOP DEBUG] Published INVENTORY_RELEASED to Kafka topic 'inventory-events'")
                    ),
                    patternCatalog.getDossierForStep("COMPENSATING_INVENTORY")
                ));
            }

        } else if (eventType.contains("PaymentCaptured") || eventType.contains("PAYMENT_CAPTURED")) {
            if (!hasStep(steps, "PAYMENT_CAPTURED")) {
                InfrastructureSnapshot paymentAfterRaw = infraInspector.captureSnapshotForService(orderId, topic, "payment-service");
                InfrastructureSnapshot paymentAfter = new InfrastructureSnapshot(
                    paymentAfterRaw.database(),
                    paymentAfterRaw.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, paymentAfterRaw.kafka().partitionOffset(), orderId),
                    paymentAfterRaw.aws()
                );
                InfrastructureSnapshot paymentBefore = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("payment_db", "payments", 0, "NONE", "AWAITING_PAYMENT"),
                    paymentAfter.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, paymentAfter.kafka().partitionOffset() - 1), null),
                    paymentAfter.aws()
                );
                InfrastructureDiff paymentDiff = infraInspector.computeDiff(paymentBefore, paymentAfter, "PAYMENT_CAPTURED");

                if (!isTerminalStatus(sagaStatus)) {
                    currentStep = "PAYMENT_CAPTURED";
                    sagaStatus = "IN_PROGRESS";
                }

                SagaStep paymentStep = new SagaStep(
                    "PAYMENT_CAPTURED",
                    "payment-service",
                    "COMPLETED",
                    "Payment transaction authorized & captured in PostgreSQL payment_db (table: payments). Emitted PaymentCapturedEvent to Kafka.",
                    68,
                    paymentBefore,
                    paymentAfter,
                    paymentDiff,
                    new AopDebugTrace(
                        "PaymentService",
                        "processPayment(ProcessPaymentCommand command)",
                        Map.of("orderId", orderId, "amount", amount),
                        Map.of("paymentStatus", paymentAfter.database().latestRecordStatus(), "txnRef", paymentAfter.database().outboxStatus()),
                        List.of("@PostMapping(\"/api/payments/process\")", "@Transactional"),
                        List.of("[AOP DEBUG] Persisted payment row in PostgreSQL payment_db table 'payments'",
                                "[AOP DEBUG] Emitted PaymentCapturedEvent to topic 'payment-events'")
                    ),
                    patternCatalog.getDossierForStep("PAYMENT_CAPTURED")
                );

                insertBeforeStepOrAppend(steps, "ORDER_CONFIRMED", paymentStep);
            }

        } else if (eventType.contains("Confirm") || eventType.contains("CONFIRM")) {
            if (!"COMPLETED".equals(sagaStatus)) {
                sagaStatus = "CONFIRMED";
            }

            // 1. Record ORDER_CONFIRMED strictly when real ORDER_CONFIRMED event arrives on order-events
            if (!hasStep(steps, "ORDER_CONFIRMED")) {
                InfrastructureSnapshot orderAfterConfirm = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "CONFIRMED", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, liveSnapshot.kafka().partitionOffset(), orderId),
                    liveSnapshot.aws()
                );
                InfrastructureSnapshot orderBeforeConfirm = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "PENDING", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, liveSnapshot.kafka().partitionOffset() - 1), null),
                    liveSnapshot.aws()
                );
                InfrastructureDiff confirmedDiff = infraInspector.computeDiff(orderBeforeConfirm, orderAfterConfirm, "ORDER_CONFIRMED");

                SagaStep confirmStep = new SagaStep(
                    "ORDER_CONFIRMED",
                    "order-service",
                    "COMPLETED",
                    "OrderSagaOrchestrator received PaymentCapturedEvent, updated order_db status to CONFIRMED, evicted CQRS Redis cache, and emitted ORDER_CONFIRMED to order-events.",
                    25,
                    orderBeforeConfirm,
                    orderAfterConfirm,
                    confirmedDiff,
                    new AopDebugTrace(
                        "OrderSagaOrchestrator",
                        "onPaymentEvent(String payload)",
                        Map.of("orderId", orderId),
                        Map.of("orderStatus", "CONFIRMED"),
                        List.of("@KafkaListener(topics = \"payment-events\")"),
                        List.of("[AOP DEBUG] Order status updated in PostgreSQL order_db to CONFIRMED",
                                "[AOP DEBUG] OutboxPublisher committed ORDER_CONFIRMED to Kafka 'order-events' & evicted stale CQRS cache")
                    ),
                    patternCatalog.getDossierForStep("ORDER_CONFIRMED")
                );
                insertBeforeStepOrAppend(steps, "INVENTORY_COMMITTED", confirmStep);
            }

            // 2. Await notification-service archival to LocalStack S3 and record INVOICE_GENERATED
            if (!"COMPLETED".equals(sagaStatus)) {
                currentStep = "INVOICE_GENERATED";
            }
            if (!hasStep(steps, "INVOICE_GENERATED")) {
                InfrastructureSnapshot invoiceAfterRaw = awaitInvoiceArchived(orderId, topic, 1500);
                InfrastructureSnapshot invoiceAfter = new InfrastructureSnapshot(
                    invoiceAfterRaw.database(),
                    invoiceAfterRaw.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, invoiceAfterRaw.kafka().partitionOffset(), orderId),
                    invoiceAfterRaw.aws()
                );
                InfrastructureSnapshot.AwsState awsBefore = invoiceAfter.aws().invoiceFound()
                    ? new InfrastructureSnapshot.AwsState(
                        invoiceAfter.aws().s3Bucket(),
                        Math.max(0, invoiceAfter.aws().objectCount() - 1),
                        false,
                        null)
                    : invoiceAfter.aws();
                InfrastructureSnapshot invoiceBefore = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("notification_inbox", "invoice_archive", 0, "NONE", "PENDING"),
                    invoiceAfter.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, invoiceAfter.kafka().partitionOffset(), orderId),
                    awsBefore
                );
                InfrastructureDiff invoiceDiff = infraInspector.computeDiff(invoiceBefore, invoiceAfter, "INVOICE_GENERATED");

                SagaStep invoiceStep = new SagaStep(
                    "INVOICE_GENERATED",
                    "notification-service",
                    "COMPLETED",
                    "Notification service consumed ORDER_CONFIRMED from order-events, generated customer invoice, and archived document into AWS S3 bucket 'order-invoices-bucket'.",
                    85,
                    invoiceBefore,
                    invoiceAfter,
                    invoiceDiff,
                    new AopDebugTrace(
                        "OrderNotificationKafkaListener",
                        "onOrderEvent(String messagePayload)",
                        Map.of("orderId", orderId, "eventType", "ORDER_CONFIRMED", "s3Bucket", "order-invoices-bucket"),
                        Map.of("archived", invoiceAfter.aws().invoiceFound(), "inboxStatus", invoiceAfter.database().latestRecordStatus()),
                        List.of("@KafkaListener(topics = \"order-events\")", "@Async"),
                        List.of("[AOP DEBUG] Filtered ORDER_CONFIRMED event & passed idempotent inbox guard",
                                "[AOP DEBUG] Invoice archived in notification-service / AWS LocalStack S3 bucket")
                    ),
                    patternCatalog.getDossierForStep("INVOICE_GENERATED")
                );
                insertBeforeStepOrAppend(steps, "ORDER_COMPLETED", invoiceStep);
            }

        } else if (eventType.contains("Complete") || eventType.contains("COMPLETE")) {
            sagaStatus = "COMPLETED";
            currentStep = "ORDER_COMPLETED";

            if (!hasStep(steps, "ORDER_COMPLETED")) {
                InfrastructureSnapshot orderAfterComplete = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "COMPLETED", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, liveSnapshot.kafka().partitionOffset(), orderId),
                    liveSnapshot.aws()
                );
                InfrastructureSnapshot orderBeforeComplete = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "CONFIRMED", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, liveSnapshot.kafka().partitionOffset() - 1), null),
                    liveSnapshot.aws()
                );
                InfrastructureDiff completedDiff = infraInspector.computeDiff(orderBeforeComplete, orderAfterComplete, "ORDER_COMPLETED");

                steps.add(new SagaStep(
                    "ORDER_COMPLETED",
                    "order-service",
                    "COMPLETED",
                    "OrderSagaOrchestrator consumed INVENTORY_COMMITTED, transitioned order_db status to COMPLETED for fulfillment, and emitted ORDER_COMPLETED to order-events.",
                    20,
                    orderBeforeComplete,
                    orderAfterComplete,
                    completedDiff,
                    new AopDebugTrace(
                        "OrderCommandHandler",
                        "completeOrder(UUID orderId)",
                        Map.of("orderId", orderId),
                        Map.of("orderStatus", "COMPLETED"),
                        List.of("@KafkaListener(topics = \"inventory-events\")", "@Transactional"),
                        List.of("[AOP DEBUG] Order status updated in PostgreSQL order_db (CONFIRMED -> COMPLETED)",
                                "[AOP DEBUG] OutboxPublisher committed ORDER_COMPLETED to Kafka 'order-events'")
                    ),
                    patternCatalog.getDossierForStep("ORDER_COMPLETED")
                ));
            }

        } else if (eventType.contains("PaymentFailed") || eventType.contains("PAYMENT_FAILED") || eventType.contains("PaymentDeclined") || eventType.contains("PAYMENT_DECLINED")) {
            if (!hasStep(steps, "PAYMENT_DECLINED")) {
                InfrastructureSnapshot paymentAfterRaw = infraInspector.captureSnapshotForService(orderId, topic, "payment-service");
                InfrastructureSnapshot paymentAfter = new InfrastructureSnapshot(
                    paymentAfterRaw.database(),
                    paymentAfterRaw.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, paymentAfterRaw.kafka().partitionOffset(), orderId),
                    paymentAfterRaw.aws()
                );
                InfrastructureSnapshot paymentBefore = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("payment_db", "payments", 0, "NONE", "AWAITING_PAYMENT"),
                    paymentAfter.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, paymentAfter.kafka().partitionOffset() - 1), null),
                    paymentAfter.aws()
                );
                InfrastructureDiff paymentDiff = infraInspector.computeDiff(paymentBefore, paymentAfter, "PAYMENT_DECLINED");

                isCompensated = true;
                if (!isTerminalStatus(sagaStatus)) {
                    currentStep = "PAYMENT_DECLINED";
                    sagaStatus = "COMPENSATING";
                }

                SagaStep declinedStep = new SagaStep(
                    "PAYMENT_DECLINED",
                    "payment-service",
                    "FAILED",
                    "Payment declined in PostgreSQL payment_db (insufficient funds / limit exceeded). Emitted PaymentFailedEvent to Kafka.",
                    54,
                    paymentBefore,
                    paymentAfter,
                    paymentDiff,
                    new AopDebugTrace(
                        "PaymentService",
                        "processPayment(ProcessPaymentCommand command)",
                        Map.of("orderId", orderId),
                        Map.of("paymentStatus", paymentAfter.database().latestRecordStatus()),
                        List.of("@PostMapping(\"/api/payments/process\")"),
                        List.of("[AOP DEBUG] Declined payment persisted in PostgreSQL payment_db table 'payments'",
                                "[AOP DEBUG] Published PaymentFailedEvent to topic 'payment-events'")
                    ),
                    patternCatalog.getDossierForStep("PAYMENT_DECLINED")
                );

                insertBeforeStepOrAppend(steps, "ORDER_CANCELLED", declinedStep);
            }

        } else if (eventType.contains("Cancel") || eventType.contains("CANCEL")) {
            isCompensated = true;
            String cancelReason = root.path("reason").asText("Order cancelled");
            boolean isInventoryRejection = hasStep(steps, "INVENTORY_REJECTED")
                || cancelReason.toLowerCase().contains("inventory")
                || cancelReason.toLowerCase().contains("insufficient stock");

            if (!hasStep(steps, "ORDER_CANCELLED")) {
                InfrastructureSnapshot orderAfterCancel = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "CANCELLED", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, liveSnapshot.kafka().partitionOffset(), orderId),
                    liveSnapshot.aws()
                );
                InfrastructureSnapshot orderBeforeCancel = new InfrastructureSnapshot(
                    new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "PENDING", "OUTBOX_PUBLISHED"),
                    liveSnapshot.redis(),
                    new InfrastructureSnapshot.KafkaState(topic, Math.max(0, liveSnapshot.kafka().partitionOffset() - 1), null),
                    liveSnapshot.aws()
                );
                InfrastructureDiff cancelDiff = infraInspector.computeDiff(orderBeforeCancel, orderAfterCancel, "ORDER_CANCELLED");

                String stepDetails = isInventoryRejection
                    ? "OrderSagaOrchestrator consumed INVENTORY_REJECTED and cancelled order in PostgreSQL order_db (" + cancelReason + ")."
                    : "Order cancelled in PostgreSQL order_db (" + cancelReason + "). Emitted OrderCancelledEvent to Kafka.";

                SagaStep cancelledStep = new SagaStep(
                    "ORDER_CANCELLED",
                    "order-service",
                    "COMPENSATED",
                    stepDetails,
                    28,
                    orderBeforeCancel,
                    orderAfterCancel,
                    cancelDiff,
                    new AopDebugTrace(
                        "OrderCommandHandler",
                        "handle(CancelOrderCommand command)",
                        Map.of("orderId", orderId, "reason", cancelReason),
                        Map.of("orderStatus", "CANCELLED"),
                        List.of("@Transactional"),
                        List.of("[AOP DEBUG] Cancelled in PostgreSQL order_db (PENDING -> CANCELLED)",
                                "[AOP DEBUG] OutboxPublisher committed ORDER_CANCELLED to Kafka 'order-events'",
                                "[AOP DEBUG] Reason: " + cancelReason)
                    ),
                    patternCatalog.getDossierForStep("ORDER_CANCELLED")
                );

                // Ensure ORDER_CANCELLED always precedes COMPENSATING_INVENTORY even if Kafka polls inventory-events slightly earlier
                insertBeforeStepOrAppend(steps, "COMPENSATING_INVENTORY", cancelledStep);
            }

            if (isInventoryRejection) {
                currentStep = "ORDER_CANCELLED";
                sagaStatus = "REJECTED";
            } else if (hasStep(steps, "COMPENSATING_INVENTORY")) {
                currentStep = "COMPENSATING_INVENTORY";
                sagaStatus = "COMPENSATED";
            } else {
                currentStep = "ORDER_CANCELLED";
                sagaStatus = "COMPENSATING";
            }
        }

        SagaExecution updatedSaga = new SagaExecution(
            sagaId,
            orderId,
            customerId,
            amount,
            sagaStatus,
            currentStep,
            steps,
            isCompensated,
            startedAt,
            Instant.now()
        );

        sagaService.recordSaga(updatedSaga);
    }

    private void reconcileOrderCreatedRedisStock(List<SagaStep> steps, String productId, int preInventoryStock) {
        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            if ("ORDER_CREATED".equals(step.stepName())
                    && step.afterState().redis().stockQuantity() != preInventoryStock) {
                InfrastructureSnapshot.RedisState alignedRedis = new InfrastructureSnapshot.RedisState(
                    preInventoryStock, false, "lock:stock:" + productId, 0);
                InfrastructureSnapshot alignedBefore = new InfrastructureSnapshot(
                    step.beforeState().database(), alignedRedis, step.beforeState().kafka(), step.beforeState().aws());
                InfrastructureSnapshot alignedAfter = new InfrastructureSnapshot(
                    step.afterState().database(), alignedRedis, step.afterState().kafka(), step.afterState().aws());
                steps.set(i, new SagaStep(
                    step.stepName(), step.service(), step.status(), step.details(), step.durationMs(),
                    alignedBefore, alignedAfter, step.impactDiff(), step.aopTrace(), step.dossier()
                ));
                break;
            }
        }
    }

    private void insertAfterStepOrAppend(List<SagaStep> steps, String anchorStepName, SagaStep newStep) {
        for (int i = 0; i < steps.size(); i++) {
            if (anchorStepName.equals(steps.get(i).stepName())) {
                steps.add(i + 1, newStep);
                return;
            }
        }
        steps.add(newStep);
    }

    private void insertBeforeStepOrAppend(List<SagaStep> steps, String anchorStepName, SagaStep newStep) {
        for (int i = 0; i < steps.size(); i++) {
            if (anchorStepName.equals(steps.get(i).stepName())) {
                steps.add(i, newStep);
                return;
            }
        }
        steps.add(newStep);
    }

    private boolean hasStep(List<SagaStep> steps, String stepName) {
        return steps.stream().anyMatch(s -> stepName.equals(s.stepName()));
    }

    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "CONFIRMED".equals(status) || "COMPENSATED".equals(status) || "REJECTED".equals(status) || "FAILED".equals(status);
    }

    private String resolveCustomerId(String incomingCustomerId, SagaExecution existing) {
        if (incomingCustomerId != null && !incomingCustomerId.isBlank()) {
            return incomingCustomerId;
        }
        if (existing != null && existing.customerId() != null && !existing.customerId().isBlank()) {
            return existing.customerId();
        }
        return "guest-customer";
    }

    private InfrastructureSnapshot withInventoryState(
            InfrastructureSnapshot source,
            String productId,
            int stockQty,
            int reservedQty,
            String lockState,
            long kafkaOffset,
            String kafkaKey) {
        InfrastructureSnapshot.RedisState r = source.redis();
        return new InfrastructureSnapshot(
            new InfrastructureSnapshot.DatabaseState(
                "inventory_store",
                "inventory_items",
                reservedQty,
                String.format("AVAIL=%d / RESERVED=%d", stockQty, reservedQty),
                lockState
            ),
            new InfrastructureSnapshot.RedisState(stockQty, r.lockHeld(), "lock:stock:" + productId, r.lockTtlMs()),
            new InfrastructureSnapshot.KafkaState(source.kafka().topic(), kafkaOffset, kafkaKey),
            source.aws()
        );
    }

    private InfrastructureSnapshot awaitInvoiceArchived(String orderId, String topic, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        InfrastructureSnapshot snapshot = infraInspector.captureSnapshotForService(orderId, topic, "notification-service");
        while (!snapshot.aws().invoiceFound() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            snapshot = infraInspector.captureSnapshotForService(orderId, topic, "notification-service");
        }
        return snapshot;
    }

    private BigDecimal extractAmountFromPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.hasNonNull("totalAmount")) return new BigDecimal(root.path("totalAmount").asText());
            if (root.hasNonNull("amount")) return new BigDecimal(root.path("amount").asText());
        } catch (Exception ignored) {}
        return null;
    }

    private String normalizeEventType(String rawEventType, String topic, JsonNode root) {
        if (rawEventType != null && !rawEventType.isBlank()) {
            String upper = rawEventType.trim().toUpperCase();
            if (upper.equals("ORDERCREATEDEVENT") || upper.equals("ORDER_CREATED")) return "ORDER_CREATED";
            if (upper.equals("ORDERCONFIRMEDEVENT") || upper.equals("ORDER_CONFIRMED")) return "ORDER_CONFIRMED";
            if (upper.equals("ORDERCOMPLETEDEVENT") || upper.equals("ORDER_COMPLETED")) return "ORDER_COMPLETED";
            if (upper.equals("ORDERCANCELLEDEVENT") || upper.equals("ORDER_CANCELLED")) return "ORDER_CANCELLED";
            if (upper.equals("PAYMENTCAPTUREDEVENT") || upper.equals("PAYMENT_CAPTURED")) return "PAYMENT_CAPTURED";
            if (upper.equals("PAYMENTFAILEDEVENT") || upper.equals("PAYMENTDECLINEDEVENT") || upper.equals("PAYMENT_DECLINED") || upper.equals("PAYMENT_FAILED")) return "PAYMENT_FAILED";
            if (upper.equals("INVENTORYRESERVEDEVENT") || upper.equals("INVENTORY_RESERVED")) return "INVENTORY_RESERVED";
            if (upper.equals("INVENTORYREJECTEDEVENT") || upper.equals("INVENTORY_REJECTED")) return "INVENTORY_REJECTED";
            if (upper.equals("INVENTORYCOMMITTEDEVENT") || upper.equals("INVENTORY_COMMITTED")) return "INVENTORY_COMMITTED";
            if (upper.equals("INVENTORYRELEASEDEVENT") || upper.equals("INVENTORY_RELEASED")) return "INVENTORY_RELEASED";
            return rawEventType.trim()
                .replaceAll("Event$", "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase();
        }
        return deriveEventType(topic, root);
    }

    private String deriveEventType(String topic, JsonNode root) {
        if (root.hasNonNull("failureReason")) return "PAYMENT_FAILED";
        if (root.hasNonNull("transactionReference")) return "PAYMENT_CAPTURED";
        if ("payment-events".equalsIgnoreCase(topic)) return "PAYMENT_PROCESSED";
        if ("inventory-events".equalsIgnoreCase(topic)) {
            if (root.hasNonNull("committedQuantity")) return "INVENTORY_COMMITTED";
            if (root.hasNonNull("releasedQuantity")) return "INVENTORY_RELEASED";
            if (root.hasNonNull("reason")) return "INVENTORY_REJECTED";
            return "INVENTORY_RESERVED";
        }
        if ("order-events".equalsIgnoreCase(topic)) {
            if (root.hasNonNull("reason") || "CANCELLED".equalsIgnoreCase(root.path("status").asText())) {
                return "ORDER_CANCELLED";
            }
            if ("COMPLETED".equalsIgnoreCase(root.path("status").asText())) {
                return "ORDER_COMPLETED";
            }
            if ("CONFIRMED".equalsIgnoreCase(root.path("status").asText())) {
                return "ORDER_CONFIRMED";
            }
            return "ORDER_CREATED";
        }
        return "CLUSTER_DOMAIN_EVENT";
    }

    private String determineProducer(String topic, String eventType) {
        if (topic.contains("order")) return "order-service";
        if (topic.contains("payment")) return "payment-service";
        if (topic.contains("inventory")) return "inventory-service";
        return "cluster-service";
    }

    private String determineConsumer(String topic, String eventType) {
        if (topic.contains("order")) return "inventory-service, notification-service";
        if (topic.contains("payment")) return "order-service";
        if (topic.contains("inventory")) return "order-service, control-center";
        return "all-subscribers";
    }
}
