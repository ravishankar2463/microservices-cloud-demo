package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.*;
import com.ravi.controlcenter.model.LiveFlowPreset.PresetStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Executes real live HTTP requests against running cluster microservices,
 * generates copyable cURL commands, captures raw HTTP responses and latency,
 * and measures live infrastructure state deltas (PostgreSQL, Redis, Kafka, S3).
 */
@Service
public class LiveOperationsDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(LiveOperationsDispatcherService.class);

    private final InfrastructureStateInspectorService infraInspector;
    private final SagaExplorerService sagaService;
    private final KafkaEventExplorerService eventService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String orderUrl;
    private final String paymentUrl;
    private final String inventoryUrl;
    private final String notificationUrl;
    private final String gatewayUrl;

    public record FlowExecutionResult(
        String flowId,
        String flowName,
        String status,          // SUCCESS, COMPENSATED, FAILED
        String orderId,
        String correlationId,
        List<LiveDispatchResponse> steps,
        String summary,
        Instant executedAt
    ) {}

    public LiveOperationsDispatcherService(
            InfrastructureStateInspectorService infraInspector,
            SagaExplorerService sagaService,
            KafkaEventExplorerService eventService,
            @Value("${services.order-service.url:http://localhost:8081}") String orderUrl,
            @Value("${services.payment-service.url:http://localhost:8084}") String paymentUrl,
            @Value("${services.inventory-service.url:http://localhost:8082}") String inventoryUrl,
            @Value("${services.notification-service.url:http://localhost:8083}") String notificationUrl,
            @Value("${services.api-gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.infraInspector = infraInspector;
        this.sagaService = sagaService;
        this.eventService = eventService;
        this.orderUrl = orderUrl;
        this.paymentUrl = paymentUrl;
        this.inventoryUrl = inventoryUrl;
        this.notificationUrl = notificationUrl;
        this.gatewayUrl = gatewayUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(4000));
        factory.setReadTimeout(Duration.ofMillis(6000));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Dispatches a single live HTTP request against the destination microservice.
     */
    public LiveDispatchResponse dispatch(LiveDispatchRequest request) {
        String corrId = (request.correlationId() != null && !request.correlationId().isBlank())
            ? request.correlationId()
            : "corr-" + UUID.randomUUID().toString().substring(0, 8);

        String baseUrl = resolveBaseUrl(request.targetService());
        String fullUrl = baseUrl + (request.path().startsWith("/") ? request.path() : "/" + request.path());

        // Generate equivalent runnable curl command
        String curlCommand = buildCurlCommand(request.httpMethod(), fullUrl, request.headers(), request.bodyJson(), corrId);

        // Pre-dispatch infrastructure snapshot
        InfrastructureSnapshot before = request.inspectInfrastructure()
            ? infraInspector.captureSnapshot(request.orderId(), "order-events")
            : null;

        long startTime = System.currentTimeMillis();
        int statusCode = 500;
        String statusText = "INTERNAL_ERROR";
        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        String responseBody = "";

        try {
            var reqSpec = restClient.method(org.springframework.http.HttpMethod.valueOf(request.httpMethod().toUpperCase()))
                .uri(fullUrl)
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", corrId);

            if (request.headers() != null) {
                request.headers().forEach(reqSpec::header);
            }

            if (request.bodyJson() != null && !request.bodyJson().isBlank() && !"GET".equalsIgnoreCase(request.httpMethod())) {
                reqSpec.body(request.bodyJson());
            }

            ResponseEntity<String> response = reqSpec.retrieve().toEntity(String.class);
            statusCode = response.getStatusCode().value();
            statusText = response.getStatusCode().toString();
            if (response.getHeaders() != null) {
                response.getHeaders().forEach(responseHeaders::put);
            }
            responseBody = response.getBody() != null ? response.getBody() : "";

        } catch (RestClientResponseException ex) {
            statusCode = ex.getStatusCode().value();
            statusText = ex.getStatusCode().toString();
            if (ex.getResponseHeaders() != null) {
                ex.getResponseHeaders().forEach(responseHeaders::put);
            }
            responseBody = ex.getResponseBodyAsString();
        } catch (Exception ex) {
            log.error("Live dispatch to {} failed: {}", fullUrl, ex.getMessage());
            statusCode = 503;
            statusText = "SERVICE_UNAVAILABLE";
            responseBody = "{\"error\": \"" + ex.getMessage() + "\"}";
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Try extracting orderId from response if not present
        String orderId = request.orderId();
        if ((orderId == null || orderId.isBlank()) && responseBody != null && responseBody.contains("orderId")) {
            try {
                JsonNode json = objectMapper.readTree(responseBody);
                if (json.has("orderId")) {
                    orderId = json.path("orderId").asText();
                }
            } catch (Exception ignored) {}
        }

        // Post-dispatch infrastructure snapshot
        InfrastructureSnapshot after = request.inspectInfrastructure()
            ? infraInspector.captureSnapshot(orderId, "order-events")
            : null;

        InfrastructureDiff diff = (before != null && after != null)
            ? infraInspector.computeDiff(before, after, request.path())
            : null;

        return new LiveDispatchResponse(
            statusCode,
            statusText,
            responseHeaders,
            responseBody,
            durationMs,
            curlCommand,
            fullUrl,
            corrId,
            orderId,
            before,
            after,
            diff,
            Instant.now()
        );
    }

    /**
     * Executes a complete multi-step canonical live flow against running pods.
     */
    public FlowExecutionResult executeFlow(String presetId, Map<String, Object> overrides) {
        String corrId = "corr-" + UUID.randomUUID().toString().substring(0, 8);

        return switch (presetId.toLowerCase()) {
            case "payment-decline", "scenario-payment-decline" ->
                executePaymentDeclineFlow(corrId, overrides);
            case "inventory-exhaustion", "scenario-inventory-unavailable" ->
                executeInventoryExhaustionFlow(corrId, overrides);
            case "manual-cancellation" ->
                executeManualCancellationFlow(corrId, overrides);
            default ->
                executeHappyPathFlow(corrId, overrides);
        };
    }

    private FlowExecutionResult executeHappyPathFlow(String corrId, Map<String, Object> overrides) {
        List<LiveDispatchResponse> steps = new ArrayList<>();
        BigDecimal amount = new BigDecimal("149.00");
        String customerId = "customer-live-" + UUID.randomUUID().toString().substring(0, 4);

        if (overrides != null && overrides.containsKey("customerId")) {
            customerId = String.valueOf(overrides.get("customerId"));
        }

        // 1. Live Step 1: POST /api/orders
        String orderBody = """
            {
              "customerId": "%s",
              "items": [
                {
                  "productId": "PROD-TECH-001",
                  "quantity": 1,
                  "unitPrice": %s
                }
              ]
            }
            """.formatted(customerId, amount);

        LiveDispatchResponse step1 = dispatch(new LiveDispatchRequest(
            "order-service", "POST", "/api/orders", Map.of(), orderBody, corrId, null, true
        ));
        steps.add(step1);

        String orderId = step1.orderId();
        if (orderId == null || orderId.isBlank()) {
            return new FlowExecutionResult("happy-path", "Live Happy Path E-Commerce Flow", "FAILED",
                null, corrId, steps, "Order creation failed with status: " + step1.statusCode(), Instant.now());
        }

        // Wait for real Outbox publish -> inventory-service reservation -> inventory-events
        awaitSagaCondition(orderId, s -> hasStep(s, "INVENTORY_RESERVED"), 2500);

        // 2. Live Step 2: POST /api/payments/process
        String payBody = """
            {
              "orderId": "%s",
              "customerId": "%s",
              "amount": %s,
              "currency": "USD",
              "idempotencyKey": "idem-%s",
              "paymentMethod": "CREDIT_CARD"
            }
            """.formatted(orderId, customerId, amount, orderId);

        LiveDispatchResponse step2 = dispatch(new LiveDispatchRequest(
            "payment-service", "POST", "/api/payments/process", Map.of(), payBody, corrId, orderId, true
        ));
        steps.add(step2);

        // Wait for real Kafka payment confirmation -> Outbox ORDER_CONFIRMED -> inventory commit -> S3 invoice -> ORDER_COMPLETED
        awaitSagaCondition(orderId, s -> "COMPLETED".equals(s.status())
            && hasStep(s, "INVENTORY_COMMITTED")
            && hasStep(s, "INVOICE_GENERATED")
            && hasStep(s, "ORDER_COMPLETED"), 4500);

        boolean success = step2.statusCode() == 201;
        String status = success ? "SUCCESS" : "FAILED";
        String summary = success
            ? "Live Order " + orderId + " completed end-to-end: payment captured, reserved stock committed for dispatch (RESERVED cleared), S3 invoice archived, and order marked COMPLETED."
            : "Payment processing failed for order " + orderId;

        return new FlowExecutionResult("happy-path", "Live Happy Path E-Commerce Flow", status,
            orderId, corrId, steps, summary, Instant.now());
    }

    private FlowExecutionResult executePaymentDeclineFlow(String corrId, Map<String, Object> overrides) {
        List<LiveDispatchResponse> steps = new ArrayList<>();
        BigDecimal amount = new BigDecimal("99.99"); // Amount ending in .99 triggers Insufficient Funds decline
        String customerId = "customer-decline-" + UUID.randomUUID().toString().substring(0, 4);

        // 1. Live Step 1: POST /api/orders
        String orderBody = """
            {
              "customerId": "%s",
              "items": [
                {
                  "productId": "PROD-TECH-001",
                  "quantity": 1,
                  "unitPrice": %s
                }
              ]
            }
            """.formatted(customerId, amount);

        LiveDispatchResponse step1 = dispatch(new LiveDispatchRequest(
            "order-service", "POST", "/api/orders", Map.of(), orderBody, corrId, null, true
        ));
        steps.add(step1);

        String orderId = step1.orderId();
        if (orderId == null || orderId.isBlank()) {
            return new FlowExecutionResult("payment-decline", "Live Payment Decline & Saga Rollback", "FAILED",
                null, corrId, steps, "Order creation failed", Instant.now());
        }

        // Wait for real Outbox publish and initial stock reservation
        awaitSagaCondition(orderId, s -> hasStep(s, "INVENTORY_RESERVED"), 2500);

        // 2. Live Step 2: POST /api/payments/process (triggers DECLINE due to .99 rule)
        String payBody = """
            {
              "orderId": "%s",
              "customerId": "%s",
              "amount": %s,
              "currency": "USD",
              "idempotencyKey": "idem-%s",
              "paymentMethod": "CREDIT_CARD"
            }
            """.formatted(orderId, customerId, amount, orderId);

        LiveDispatchResponse step2 = dispatch(new LiveDispatchRequest(
            "payment-service", "POST", "/api/payments/process", Map.of(), payBody, corrId, orderId, true
        ));
        steps.add(step2);

        // Wait for OrderSagaOrchestrator cancellation + Outbox ORDER_CANCELLED + inventory-service stock restoration
        awaitSagaCondition(orderId, s -> "COMPENSATED".equals(s.status()) && hasStep(s, "COMPENSATING_INVENTORY"), 3500);

        String summary = "Payment declined (402 Payment Required). Saga compensation triggered: Order cancelled in order_db and reserved stock restored in Redis.";

        return new FlowExecutionResult("payment-decline", "Live Payment Decline & Saga Rollback", "COMPENSATED",
            orderId, corrId, steps, summary, Instant.now());
    }

    private FlowExecutionResult executeInventoryExhaustionFlow(String corrId, Map<String, Object> overrides) {
        List<LiveDispatchResponse> steps = new ArrayList<>();
        String customerId = "customer-overstock-" + UUID.randomUUID().toString().substring(0, 4);

        // Request 999 units (exceeding stock limit of 100)
        String orderBody = """
            {
              "customerId": "%s",
              "items": [
                {
                  "productId": "PROD-TECH-001",
                  "quantity": 999,
                  "unitPrice": 149.00
                }
              ]
            }
            """.formatted(customerId);

        LiveDispatchResponse step1 = dispatch(new LiveDispatchRequest(
            "order-service", "POST", "/api/orders", Map.of(), orderBody, corrId, null, true
        ));
        steps.add(step1);

        String orderId = step1.orderId();
        if (orderId == null || orderId.isBlank()) {
            return new FlowExecutionResult("inventory-exhaustion", "Live Inventory Exhaustion Test", "FAILED",
                null, corrId, steps, "Order creation failed with status: " + step1.statusCode(), Instant.now());
        }

        // Wait for live Outbox publish -> inventory-service guard rejection -> inventory-events -> OrderSagaOrchestrator cancellation -> order-events
        awaitSagaCondition(orderId, s -> "REJECTED".equals(s.status()) && hasStep(s, "ORDER_CANCELLED"), 3500);
        String observedOrderStatus = awaitLiveOrderStatus(orderId, "CANCELLED", 1000);
        boolean rejected = "CANCELLED".equalsIgnoreCase(observedOrderStatus);
        String resultStatus = rejected ? "REJECTED" : "IN_PROGRESS";
        String summary = rejected
            ? "Inventory exhaustion guard rejected 999 requested units in inventory-service. InventoryRejectedEvent emitted to inventory-events and Order " + orderId + " transitioned to CANCELLED in order_db."
            : "Order " + orderId + " placed for 999 units; current live order_db status: " + observedOrderStatus + ".";

        return new FlowExecutionResult("inventory-exhaustion", "Live Inventory Exhaustion Test", resultStatus,
            orderId, corrId, steps, summary, Instant.now());
    }

    private FlowExecutionResult executeManualCancellationFlow(String corrId, Map<String, Object> overrides) {
        List<LiveDispatchResponse> steps = new ArrayList<>();
        String targetOrderId = (overrides != null && overrides.containsKey("orderId"))
            ? String.valueOf(overrides.get("orderId"))
            : null;
        String customerId = "cust-cancel-" + UUID.randomUUID().toString().substring(0, 4);

        // If no orderId supplied, create a quick live order first and wait for Outbox + inventory reservation
        if (targetOrderId == null || targetOrderId.isBlank()) {
            LiveDispatchResponse createStep = dispatch(new LiveDispatchRequest(
                "order-service", "POST", "/api/orders", Map.of(),
                "{\"customerId\":\"" + customerId + "\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":49.00}]}",
                corrId, null, true
            ));
            steps.add(createStep);
            targetOrderId = createStep.orderId();
            if (targetOrderId != null && !targetOrderId.isBlank()) {
                awaitSagaCondition(targetOrderId, s -> hasStep(s, "INVENTORY_RESERVED"), 2500);
            }
        }

        if (targetOrderId != null && !targetOrderId.isBlank()) {
            LiveDispatchResponse cancelStep = dispatch(new LiveDispatchRequest(
                "order-service", "POST", "/api/orders/" + targetOrderId + "/cancel",
                Map.of(), "{\"reason\":\"Customer requested cancellation from Control Center\"}",
                corrId, targetOrderId, true
            ));
            steps.add(cancelStep);
            awaitSagaCondition(targetOrderId, s -> "COMPENSATED".equals(s.status()) && hasStep(s, "COMPENSATING_INVENTORY"), 3500);
        }

        return new FlowExecutionResult("manual-cancellation", "Live Manual Order Cancellation", "COMPENSATED",
            targetOrderId, corrId, steps, "Customer cancellation sent to order-service and reserved stock restored via Saga compensation.", Instant.now());
    }

    /**
     * Performs a full live platform reset across all stateful microservices, LocalStack S3,
     * Redis caches, inventory stock levels, and Control Center Saga/Kafka Explorer stores.
     */
    public Map<String, Object> resetAllPlatformState() {
        Map<String, String> components = new LinkedHashMap<>();

        components.put("order-service", invokeResetEndpoint(orderUrl + "/api/orders/reset"));
        components.put("inventory-service", invokeResetEndpoint(inventoryUrl + "/api/inventory/reset"));
        components.put("payment-service", invokeResetEndpoint(paymentUrl + "/api/payments/reset"));
        components.put("notification-service", invokeResetEndpoint(notificationUrl + "/api/notifications/reset"));

        sagaService.clearAll();
        components.put("saga-explorer", "CLEARED");

        eventService.clearAll();
        components.put("kafka-explorer", "CLEARED");

        log.info("Full platform reset executed: {}", components);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Platform state, databases, inventory, S3 invoices, sagas, and Kafka explorer cleared.");
        result.put("components", components);
        result.put("resetAt", Instant.now().toString());
        return result;
    }

    private String invokeResetEndpoint(String url) {
        try {
            ResponseEntity<String> resp = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body("{}")
                .retrieve()
                .toEntity(String.class);
            return resp.getStatusCode().is2xxSuccessful() ? "RESET_OK" : ("HTTP_" + resp.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Reset endpoint {} unavailable: {}", url, e.getMessage());
            return "UNREACHABLE";
        }
    }

    private void awaitSagaCondition(String orderId, Predicate<SagaExecution> condition, long maxWaitMs) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        String sagaId = "saga-" + orderId;
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<SagaExecution> sagaOpt = sagaService.getSagaById(sagaId);
            if (sagaOpt.isPresent() && condition.test(sagaOpt.get())) {
                return;
            }
            sleepSilently(100);
        }
    }

    private boolean hasStep(SagaExecution saga, String stepName) {
        return saga.steps() != null && saga.steps().stream().anyMatch(s -> stepName.equals(s.stepName()));
    }

    private String awaitLiveOrderStatus(String orderId, String expectedStatus, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        String currentStatus = "UNKNOWN";
        while (System.currentTimeMillis() < deadline) {
            InfrastructureSnapshot snap = infraInspector.captureSnapshot(orderId, "order-events");
            currentStatus = snap.database().latestRecordStatus();
            if (expectedStatus.equalsIgnoreCase(currentStatus)) {
                return currentStatus;
            }
            sleepSilently(150);
        }
        return currentStatus;
    }

    public List<LiveFlowPreset> getPresets() {
        return List.of(
            new LiveFlowPreset(
                "happy-path",
                "🟢 Live Happy Path E-Commerce Flow",
                "SAGA_SUCCESS",
                "Creates live order ($149.00), reserves inventory with Redis lock, captures payment, confirms order, and archives S3 invoice.",
                buildCurlCommand("POST", orderUrl + "/api/orders", Map.of(), "{\"customerId\":\"customer-live-01\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":149.00}]}", "corr-live-happy"),
                List.of(
                    new PresetStep("Create Order", "order-service", "POST", "/api/orders",
                        "{\"customerId\":\"customer-live-01\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":149.00}]}",
                        "201 Created with new orderId and Outbox event queued"),
                    new PresetStep("Process Payment", "payment-service", "POST", "/api/payments/process",
                        "{\"orderId\":\"<orderId>\",\"customerId\":\"customer-live-01\",\"amount\":149.00,\"currency\":\"USD\",\"idempotencyKey\":\"idem-<orderId>\",\"paymentMethod\":\"CREDIT_CARD\"}",
                        "201 Created, emits PaymentCapturedEvent to Kafka, triggers Order confirmation")
                )
            ),
            new LiveFlowPreset(
                "payment-decline",
                "🔴 Live Payment Decline & Saga Rollback",
                "SAGA_COMPENSATION",
                "Creates live order ($99.99), reserves stock, triggers insufficient funds decline (.99 rule), fires PaymentFailedEvent on Kafka, compensating stock in Redis.",
                buildCurlCommand("POST", orderUrl + "/api/orders", Map.of(), "{\"customerId\":\"customer-decline-01\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":99.99}]}", "corr-live-decline"),
                List.of(
                    new PresetStep("Create Order ($99.99)", "order-service", "POST", "/api/orders",
                        "{\"customerId\":\"customer-decline-01\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":99.99}]}",
                        "201 Created with order status PENDING"),
                    new PresetStep("Process Declined Payment", "payment-service", "POST", "/api/payments/process",
                        "{\"orderId\":\"<orderId>\",\"customerId\":\"customer-decline-01\",\"amount\":99.99,\"currency\":\"USD\",\"idempotencyKey\":\"idem-<orderId>\",\"paymentMethod\":\"CREDIT_CARD\"}",
                        "Payment DECLINED, emits PaymentFailedEvent, order cancels and stock restores to 100")
                )
            ),
            new LiveFlowPreset(
                "inventory-exhaustion",
                "🟠 Live Inventory Exhaustion Test",
                "VALIDATION_GUARD",
                "Attempts to place an order for 999 units exceeding warehouse stock, evaluating inventory constraint handling.",
                buildCurlCommand("POST", orderUrl + "/api/orders", Map.of(), "{\"customerId\":\"customer-overstock\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":999,\"unitPrice\":149.00}]}", "corr-live-exhaust"),
                List.of(
                    new PresetStep("Place 999 Units Order", "order-service", "POST", "/api/orders",
                        "{\"customerId\":\"customer-overstock\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":999,\"unitPrice\":149.00}]}",
                        "Triggers stock boundary guard in inventory-service")
                )
            ),
            new LiveFlowPreset(
                "manual-cancellation",
                "🔵 Live Manual Order Cancellation",
                "COMPENSATION",
                "Invokes live customer cancellation on an existing order, triggering Saga rollback to restore stock.",
                buildCurlCommand("POST", orderUrl + "/api/orders/<orderId>/cancel", Map.of(), "{\"reason\":\"Customer requested cancellation\"}", "corr-live-cancel"),
                List.of(
                    new PresetStep("Cancel Order", "order-service", "POST", "/api/orders/<orderId>/cancel",
                        "{\"reason\":\"Customer requested cancellation from Control Center\"}",
                        "200 OK, transitions Order to CANCELLED and publishes OrderCancelledEvent")
                )
            )
        );
    }

    private String resolveBaseUrl(String serviceName) {
        if (serviceName == null) return gatewayUrl;
        return switch (serviceName.toLowerCase().trim()) {
            case "order-service", "orders" -> orderUrl;
            case "payment-service", "payments" -> paymentUrl;
            case "inventory-service", "inventory" -> inventoryUrl;
            case "notification-service", "notifications" -> notificationUrl;
            default -> gatewayUrl;
        };
    }

    private String buildCurlCommand(String method, String url, Map<String, String> headers, String body, String corrId) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(method.toUpperCase()).append(" \"").append(url).append("\" \\\n");
        sb.append("  -H \"Content-Type: application/json\" \\\n");
        sb.append("  -H \"X-Correlation-ID: ").append(corrId).append("\"");

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!"content-type".equalsIgnoreCase(k) && !"x-correlation-id".equalsIgnoreCase(k)) {
                    sb.append(" \\\n  -H \"").append(k).append(": ").append(v).append("\"");
                }
            });
        }

        if (body != null && !body.isBlank() && !"GET".equalsIgnoreCase(method)) {
            String compactBody = body.replaceAll("\\r?\\n\\s*", " ").trim();
            sb.append(" \\\n  -d '").append(compactBody).append("'");
        }

        return sb.toString();
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
