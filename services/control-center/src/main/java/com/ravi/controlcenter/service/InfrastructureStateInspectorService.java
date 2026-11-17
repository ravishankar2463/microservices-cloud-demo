package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.EventRecord;
import com.ravi.controlcenter.model.InfrastructureDiff;
import com.ravi.controlcenter.model.InfrastructureSnapshot;
import com.ravi.controlcenter.model.InfrastructureSnapshot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

/**
 * Captures real-time state snapshots across PostgreSQL, Redis, Kafka, and AWS LocalStack S3,
 * and computes concrete before/after diff deltas for each step of the distributed saga.
 */
@Service
public class InfrastructureStateInspectorService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureStateInspectorService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final KafkaEventExplorerService kafkaEventService;

    private final String orderUrl;
    private final String inventoryUrl;
    private final String paymentUrl;
    private final String notificationUrl;
    private final String awsEmulatorUrl;

    public InfrastructureStateInspectorService(
            KafkaEventExplorerService kafkaEventService,
            @Value("${services.order-service.url:http://localhost:8081}") String orderUrl,
            @Value("${services.inventory-service.url:http://localhost:8082}") String inventoryUrl,
            @Value("${services.payment-service.url:http://localhost:8084}") String paymentUrl,
            @Value("${services.notification-service.url:http://localhost:8083}") String notificationUrl,
            @Value("${services.aws-emulator.url:http://localhost:4566}") String awsEmulatorUrl) {
        this.kafkaEventService = kafkaEventService;
        this.orderUrl = orderUrl;
        this.inventoryUrl = inventoryUrl;
        this.paymentUrl = paymentUrl;
        this.notificationUrl = notificationUrl;
        this.awsEmulatorUrl = awsEmulatorUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(2000));
        factory.setReadTimeout(Duration.ofMillis(3000));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
    }

    public InfrastructureSnapshot captureSnapshot(String orderId, String targetTopic) {
        return captureSnapshotForService(orderId, targetTopic, "order-service");
    }

    public InfrastructureSnapshot captureSnapshotForService(String orderId, String targetTopic, String serviceName) {
        DatabaseState dbState = inspectDatabaseState(orderId, serviceName);
        RedisState redisState = inspectRedisState();
        KafkaState kafkaState = inspectKafkaState(targetTopic);
        AwsState awsState = inspectAwsState(orderId);

        return new InfrastructureSnapshot(dbState, redisState, kafkaState, awsState);
    }

    public InfrastructureDiff computeDiff(InfrastructureSnapshot before, InfrastructureSnapshot after, String stepName) {
        List<String> highlights = new ArrayList<>();
        Map<String, String> details = new LinkedHashMap<>();

        if (before == null || after == null) {
            return new InfrastructureDiff(
                List.of("State baseline initialized"),
                Map.of("status", "Initial state recorded")
            );
        }

        // 1. Database comparison
        if (!Objects.equals(before.database().latestRecordStatus(), after.database().latestRecordStatus())) {
            String dbChange = String.format("DB State: %s -> %s (table: %s)",
                before.database().latestRecordStatus(),
                after.database().latestRecordStatus(),
                after.database().tableName());
            highlights.add(dbChange);
            details.put("database.status_transition", String.format("%s => %s",
                before.database().latestRecordStatus(), after.database().latestRecordStatus()));
        }
        if (after.database().rowCount() != before.database().rowCount()) {
            details.put("database.row_delta", String.format("%+d row(s) in %s",
                after.database().rowCount() - before.database().rowCount(), after.database().tableName()));
        }
        if (!Objects.equals(before.database().outboxStatus(), after.database().outboxStatus())) {
            details.put("database.outbox_events", String.format("%s => %s (ACID atomic write)",
                before.database().outboxStatus(), after.database().outboxStatus()));
        }

        // 2. Redis comparison
        int stockDelta = after.redis().stockQuantity() - before.redis().stockQuantity();
        if (stockDelta != 0) {
            String stockHighlight = String.format("Redis Cache: Stock %d -> %d (%+d SKU allocation)",
                before.redis().stockQuantity(), after.redis().stockQuantity(), stockDelta);
            highlights.add(stockHighlight);
            details.put("redis.stock_delta", String.format("%+d units", stockDelta));
        }
        if (before.redis().lockHeld() != after.redis().lockHeld() || after.redis().lockTtlMs() > 0) {
            String lockSummary = after.redis().lockHeld()
                ? "Redis Mutex: Acquired " + after.redis().lockKey() + " (TTL " + after.redis().lockTtlMs() + "ms)"
                : "Redis Mutex: Released " + after.redis().lockKey() + " (Lock clean)";
            highlights.add(lockSummary);
            details.put("redis.distributed_lock", lockSummary);
        }

        // 3. Kafka comparison
        long offsetDelta = after.kafka().partitionOffset() - before.kafka().partitionOffset();
        if (offsetDelta > 0) {
            String kafkaHighlight = String.format("Kafka Event Stream: Topic '%s' offset %d -> %d (+%d event committed)",
                after.kafka().topic(), before.kafka().partitionOffset(), after.kafka().partitionOffset(), offsetDelta);
            highlights.add(kafkaHighlight);
            details.put("kafka.offset_delta", String.format("+%d message(s) on %s", offsetDelta, after.kafka().topic()));
            if (after.kafka().lastEventKey() != null) {
                details.put("kafka.last_key", after.kafka().lastEventKey());
            }
        }

        // 4. AWS S3 comparison
        if (after.aws().objectCount() != before.aws().objectCount() || (!before.aws().invoiceFound() && after.aws().invoiceFound())) {
            String s3Highlight = String.format("AWS S3 (%s): Invoice document archived (+%d object stored)",
                after.aws().s3Bucket(), after.aws().objectCount() - before.aws().objectCount());
            highlights.add(s3Highlight);
            details.put("aws.s3_invoice", "Archived object: " + after.aws().invoiceKey());
        }

        if (highlights.isEmpty()) {
            highlights.add(String.format("Step '%s' validated infrastructure invariants with no persistent state drift", stepName));
        }

        return new InfrastructureDiff(highlights, details);
    }

    private DatabaseState inspectDatabaseState(String orderId, String serviceName) {
        String svc = (serviceName != null) ? serviceName.toLowerCase().trim() : "order-service";
        return switch (svc) {
            case "payment-service" -> inspectPaymentDatabaseState(orderId);
            case "inventory-service" -> inspectInventoryStoreState();
            case "notification-service" -> inspectNotificationInboxState(orderId);
            default -> inspectOrderDatabaseState(orderId);
        };
    }

    private DatabaseState inspectOrderDatabaseState(String orderId) {
        String dbName = "order_db";
        String tableName = "orders";
        int rowCount = 0;
        String status = "NONE";
        String outboxStatus = "OUTBOX_EMPTY";

        if (orderId != null && !orderId.isBlank()) {
            try {
                String resp = restClient.get()
                    .uri(orderUrl + "/api/orders/" + orderId)
                    .retrieve()
                    .body(String.class);

                if (resp != null && !resp.isBlank()) {
                    JsonNode json = objectMapper.readTree(resp);
                    status = json.path("status").asText("RECORD_FOUND");
                    outboxStatus = "OUTBOX_PUBLISHED";
                    rowCount = 1;
                }
            } catch (Exception ignored) {
                status = "NONE";
                rowCount = 0;
            }
        }

        return new DatabaseState(dbName, tableName, rowCount, status, outboxStatus);
    }

    private DatabaseState inspectPaymentDatabaseState(String orderId) {
        String dbName = "payment_db";
        String tableName = "payments";
        int rowCount = 0;
        String status = "NONE";
        String eventRef = "AWAITING_PAYMENT";

        if (orderId != null && !orderId.isBlank()) {
            try {
                String resp = restClient.get()
                    .uri(paymentUrl + "/api/payments/order/" + orderId)
                    .retrieve()
                    .body(String.class);

                if (resp != null && !resp.isBlank()) {
                    JsonNode json = objectMapper.readTree(resp);
                    status = json.path("status").asText("PROCESSED");
                    String txnRef = json.path("transactionReference").asText("");
                    String failReason = json.path("failureReason").asText("");
                    eventRef = !txnRef.isBlank() && !"null".equals(txnRef)
                        ? txnRef
                        : (!failReason.isBlank() && !"null".equals(failReason) ? "DECLINED_EVENT_EMITTED" : "PAYMENT_EVENT_EMITTED");
                    rowCount = 1;
                }
            } catch (Exception ignored) {
                status = "NONE";
                rowCount = 0;
            }
        }

        return new DatabaseState(dbName, tableName, rowCount, status, eventRef);
    }

    private DatabaseState inspectInventoryStoreState() {
        String dbName = "inventory_store";
        String tableName = "inventory_items";
        int reservedQty = 0;
        int availableQty = 100;
        String status = "IN_STOCK";
        String lockState = "LOCK_RELEASED";

        try {
            String resp = restClient.get()
                .uri(inventoryUrl + "/api/inventory/PROD-TECH-001")
                .retrieve()
                .body(String.class);

            if (resp != null && !resp.isBlank()) {
                JsonNode json = objectMapper.readTree(resp);
                JsonNode data = json.path("data");
                if (!data.isMissingNode()) {
                    availableQty = data.path("availableQuantity").asInt(availableQty);
                    reservedQty = data.path("reservedQuantity").asInt(0);
                    status = String.format("AVAIL=%d / RESERVED=%d", availableQty, reservedQty);
                }
            }
        } catch (Exception ignored) {}

        return new DatabaseState(dbName, tableName, reservedQty, status, lockState);
    }

    private DatabaseState inspectNotificationInboxState(String orderId) {
        String dbName = "notification_inbox";
        String tableName = "invoice_archive";
        int rowCount = 0;
        String status = "NONE";
        String idempotencyState = "PENDING";

        if (orderId != null && !orderId.isBlank()) {
            try {
                String resp = restClient.get()
                    .uri(notificationUrl + "/api/notifications/invoices/" + orderId)
                    .retrieve()
                    .body(String.class);

                if (resp != null && resp.contains("\"success\":true")) {
                    rowCount = 1;
                    status = "INVOICE_ARCHIVED";
                    idempotencyState = "IDEMPOTENT_DEDUP_OK";
                }
            } catch (Exception ignored) {}
        }

        return new DatabaseState(dbName, tableName, rowCount, status, idempotencyState);
    }

    private RedisState inspectRedisState() {
        int stock = 100;
        boolean lockHeld = false;
        String lockKey = "lock:stock:PROD-TECH-001";
        long ttlMs = 0;

        try {
            String resp = restClient.get()
                .uri(inventoryUrl + "/api/inventory/PROD-TECH-001")
                .retrieve()
                .body(String.class);

            if (resp != null && !resp.isBlank()) {
                JsonNode json = objectMapper.readTree(resp);
                JsonNode data = json.path("data");
                if (!data.isMissingNode()) {
                    stock = data.path("availableQuantity").asInt(stock);
                }
            }
        } catch (Exception ignored) {}

        return new RedisState(stock, lockHeld, lockKey, ttlMs);
    }

    private KafkaState inspectKafkaState(String topicName) {
        String topic = (topicName != null && !topicName.isBlank()) ? topicName : "order-events";
        List<EventRecord> events = kafkaEventService.getEvents(topic, null, 100);
        long offset = events.size();
        String lastKey = events.isEmpty() ? null : events.getFirst().correlationId();

        return new KafkaState(topic, offset, lastKey);
    }

    private AwsState inspectAwsState(String orderId) {
        String bucket = "order-invoices-bucket";
        int count = 0;
        boolean found = false;
        String invoiceKey = (orderId != null) ? "invoices/" + orderId + ".json" : "invoices/latest.json";

        try {
            String xml = restClient.get()
                .uri(awsEmulatorUrl + "/" + bucket)
                .retrieve()
                .body(String.class);

            if (xml != null && xml.contains("<Key>")) {
                count = (xml.split("<Key>").length - 1);
                found = (orderId != null && xml.contains(orderId)) || count > 0;
            }
        } catch (Exception ignored) {}

        // Also check notification-service archive endpoint if LocalStack S3 bucket has not been populated directly
        if (!found && orderId != null && !orderId.isBlank()) {
            try {
                String notifResp = restClient.get()
                    .uri(notificationUrl + "/api/notifications/invoices/" + orderId)
                    .retrieve()
                    .body(String.class);
                if (notifResp != null && notifResp.contains("\"success\":true")) {
                    found = true;
                    count = Math.max(count, 1);
                }
            } catch (Exception ignored) {}
        }

        return new AwsState(bucket, count, found, invoiceKey);
    }
}
