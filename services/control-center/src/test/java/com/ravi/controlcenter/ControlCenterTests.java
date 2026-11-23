package com.ravi.controlcenter;

import com.ravi.controlcenter.messaging.LiveKafkaSagaConsumer;
import com.ravi.controlcenter.model.*;
import com.ravi.controlcenter.service.*;
import com.ravi.controlcenter.service.LiveOperationsDispatcherService.FlowExecutionResult;
import com.ravi.controlcenter.web.ControlCenterApiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ControlCenterTests {

    @Autowired
    private ControlCenterApiController controller;

    @Autowired
    private ServiceRegistryService registryService;

    @Autowired
    private ArchitectureTopologyService topologyService;

    @Autowired
    private KafkaEventExplorerService eventService;

    @Autowired
    private SagaExplorerService sagaService;

    @Autowired
    private LearningCenterService learningService;

    @Autowired
    private LiveOperationsDispatcherService liveDispatcher;

    @Autowired
    private AwsEmulatorService awsEmulatorService;

    @Autowired
    private LiveKafkaSagaConsumer liveKafkaSagaConsumer;

    @Autowired
    private InfrastructureStateInspectorService infraInspector;

    @Autowired
    private LocalEditorLauncherService editorLauncherService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    @DisplayName("Should successfully load ApplicationContext")
    void contextLoads() {
        assertThat(controller).isNotNull();
        assertThat(registryService).isNotNull();
        assertThat(topologyService).isNotNull();
    }

    @Test
    @DisplayName("Should return valid system overview with metrics and health")
    void shouldReturnSystemOverview() {
        ResponseEntity<SystemOverview> response = controller.getSystemOverview();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        SystemOverview overview = response.getBody();
        assertThat(overview).isNotNull();
        assertThat(overview.totalServices()).isEqualTo(16);
        assertThat(overview.kafkaTopicsCount()).isGreaterThanOrEqualTo(4);
        assertThat(overview.environment()).isEqualTo("LOCAL_DEVELOPMENT");
    }

    @Test
    @DisplayName("Should discover all 8 services, 3 observability nodes, and 5 INFRA nodes in service registry")
    void shouldDiscoverAllServices() {
        ResponseEntity<List<ServiceNode>> response = controller.getAllServices();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<ServiceNode> nodes = response.getBody();
        assertThat(nodes).isNotNull().hasSize(16);

        List<String> serviceIds = nodes.stream().map(ServiceNode::id).toList();
        assertThat(serviceIds).contains(
            "api-gateway",
            "order-service",
            "inventory-service",
            "notification-service",
            "payment-service",
            "product-service",
            "keycloak",
            "aws-emulator",
            "kafka",
            "inventory-redis",
            "order-db",
            "product-db",
            "payment-db"
        );

        List<ServiceNode> infraNodes = nodes.stream().filter(n -> "INFRA".equals(n.type())).toList();
        assertThat(infraNodes).hasSize(5);
        assertThat(infraNodes).extracting(ServiceNode::id)
            .containsExactlyInAnyOrder("kafka", "inventory-redis", "order-db", "product-db", "payment-db");
    }

    @Test
    @DisplayName("Should return architecture topology with nodes and communication edges")
    void shouldReturnArchitectureTopology() {
        ResponseEntity<ArchitectureTopology> response = controller.getArchitectureTopology();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArchitectureTopology topology = response.getBody();
        assertThat(topology).isNotNull();
        assertThat(topology.nodes()).hasSize(16); // 8 services + 3 observability + 5 infra (kafka, redis, 3 dbs)
        assertThat(topology.edges()).hasSize(17);

        // Check specific communication types
        List<String> edgeTypes = topology.edges().stream().map(TopologyEdge::type).toList();
        assertThat(edgeTypes).contains("SYNC_REST", "ASYNC_KAFKA", "PERSISTENCE", "CACHE", "CLOUD_INTEGRATION");
    }

    @Test
    @DisplayName("Should return Kafka event history and mask sensitive card data")
    void shouldReturnKafkaEventsWithMasking() {
        // Record test event with sensitive card data and unquoted numeric values
        eventService.recordEvent(new EventRecord(
            "test-evt-99",
            "PaymentRequestedEvent",
            "payment-events",
            "order-service",
            "payment-service",
            "CONSUMED",
            "{\"orderId\":\"ord-1\",\"cardNumber\":\"4111222233334444\",\"cvv\":123,\"secret\":\"supersecrettoken\",\"amount\":50.00}",
            "corr-99",
            null
        ));

        ResponseEntity<List<EventRecord>> response = controller.getEvents("payment-events", null, 10);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<EventRecord> events = response.getBody();
        assertThat(events).isNotNull().isNotEmpty();

        EventRecord masked = events.stream()
            .filter(e -> "test-evt-99".equals(e.eventId()))
            .findFirst()
            .orElseThrow();

        assertThat(masked.maskedPayload()).doesNotContain("4111222233334444");
        assertThat(masked.maskedPayload()).doesNotContain("123");
        assertThat(masked.maskedPayload()).doesNotContain("supersecrettoken");
        assertThat(masked.maskedPayload()).contains("****-****-****-XXXX");
    }

    @Test
    @DisplayName("Should provide Live Flow Presets and execute live request formatting")
    void shouldProvideLiveFlowPresetsAndDispatcher() {
        // 1. Verify Live Presets endpoint
        ResponseEntity<List<LiveFlowPreset>> presetsResp = controller.getLivePresets();
        assertThat(presetsResp.getStatusCode().is2xxSuccessful()).isTrue();
        List<LiveFlowPreset> presets = presetsResp.getBody();
        assertThat(presets).isNotNull().hasSizeGreaterThanOrEqualTo(4);

        LiveFlowPreset happyPreset = presets.stream()
            .filter(p -> "happy-path".equals(p.id()))
            .findFirst()
            .orElseThrow();
        assertThat(happyPreset.curlCommand()).contains("curl -X POST");
        assertThat(happyPreset.curlCommand()).contains("http");
        assertThat(happyPreset.curlCommand()).contains("X-Correlation-ID");

        // 2. Verify Single Live Dispatch request generation and failure handling (when service is local)
        LiveDispatchRequest dispatchReq = new LiveDispatchRequest(
            "order-service",
            "POST",
            "/api/orders",
            java.util.Map.of(),
            "{\"customerId\":\"test-unit\",\"items\":[{\"productId\":\"PROD-TECH-001\",\"quantity\":1,\"unitPrice\":149.00}]}",
            "corr-test-unit",
            null,
            false
        );
        LiveDispatchResponse dispatchResp = liveDispatcher.dispatch(dispatchReq);
        assertThat(dispatchResp).isNotNull();
        assertThat(dispatchResp.curlCommand()).contains("curl -X POST");
        assertThat(dispatchResp.curlCommand()).contains("/api/orders");
        assertThat(dispatchResp.correlationId()).isEqualTo("corr-test-unit");

        // 3. Test Masterclass Pattern Dossier Endpoint
        ResponseEntity<ArchitecturalDossier> dossierResp = controller.getStepDossier("ORDER_CREATED");
        assertThat(dossierResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(dossierResp.getBody()).isNotNull();
        assertThat(dossierResp.getBody().whatItDid()).contains("outbox_events");
        assertThat(dossierResp.getBody().architecturalSignificance()).contains("Dual-Write Hazard");
        assertThat(dossierResp.getBody().hyperscaleBlueprint()).isNotNull();
    }

    @Test
    @DisplayName("Should query infrastructure nodes and logs without exceptions")
    void shouldQueryInfraNodesAndLogs() {
        ResponseEntity<List<InfraNodeInfo>> nodesResp = controller.getInfraNodes(null);
        assertThat(nodesResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(nodesResp.getBody()).isNotNull();

        // Safe query with invalid name should return error message instead of failing
        ResponseEntity<java.util.Map<String, String>> logsResp = controller.getInfraLogs("k8s", "safe-test-pod", 50);
        assertThat(logsResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(logsResp.getBody()).containsKey("logs");
    }

    @Test
    @DisplayName("Should query and parse AWS LocalStack emulator health and services")
    void shouldQueryAndParseAwsEmulatorHealth() {
        String sampleJson = "{\"services\": {\"acm\": \"disabled\", \"dynamodb\": \"available\", \"dynamodbstreams\": \"available\", \"kinesis\": \"available\", \"s3\": \"running\", \"sns\": \"available\", \"sqs\": \"available\"}, \"edition\": \"community\", \"version\": \"3.7.2\"}";
        AwsEmulatorHealth parsed = awsEmulatorService.parseHealthResponse(sampleJson);

        assertThat(parsed).isNotNull();
        assertThat(parsed.edition()).isEqualTo("community");
        assertThat(parsed.version()).isEqualTo("3.7.2");
        assertThat(parsed.status()).isEqualTo("ONLINE");
        assertThat(parsed.totalServices()).isEqualTo(7);
        assertThat(parsed.availableCount()).isEqualTo(6);
        assertThat(parsed.runningCount()).isEqualTo(1);
        assertThat(parsed.disabledCount()).isEqualTo(1);
        assertThat(parsed.activeServices()).hasSize(6);

        // Verify S3 active details
        AwsEmulatorHealth.AwsServiceDetail s3 = parsed.activeServices().stream()
                .filter(s -> s.name().contains("S3"))
                .findFirst()
                .orElseThrow();
        assertThat(s3.category()).isEqualTo("Storage");
        assertThat(s3.status()).isEqualTo("running");
        assertThat(s3.active()).isTrue();
        assertThat(s3.cliCommand()).contains("s3");

        // Verify controller endpoint
        ResponseEntity<AwsEmulatorHealth> controllerResp = controller.getAwsEmulatorHealth();
        assertThat(controllerResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controllerResp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should build accurate Inventory Exhaustion Saga strictly from real Kafka events without fabricating INVENTORY_RESERVED")
    void shouldTrackInventoryExhaustionSagaAccurately() {
        String orderId = "exhaust-" + java.util.UUID.randomUUID();
        String sagaId = "saga-" + orderId;

        // 1. ORDER_CREATED arrives -> must ONLY add ORDER_CREATED, never fabricate INVENTORY_RESERVED
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"customer-overstock-test","totalAmount":148851.00}
            """.formatted(orderId), "order-events", orderId, 1L);

        SagaExecution afterCreate = sagaService.getSagaById(sagaId).orElseThrow();
        assertThat(afterCreate.status()).isEqualTo("IN_PROGRESS");
        assertThat(afterCreate.customerId()).isEqualTo("customer-overstock-test");
        assertThat(afterCreate.steps()).extracting(SagaExecution.SagaStep::stepName).containsExactly("ORDER_CREATED");

        // 2. INVENTORY_REJECTED arrives on inventory-events -> FAILED step with zero stock mutation
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_REJECTED","orderId":"%s","customerId":"customer-overstock-test","productId":"PROD-TECH-001","requestedQuantity":999,"availableQuantity":100,"reservedQuantity":0,"reason":"Insufficient stock for PROD-TECH-001: requested 999, available 100"}
            """.formatted(orderId), "inventory-events", orderId, 2L);

        SagaExecution afterReject = sagaService.getSagaById(sagaId).orElseThrow();
        assertThat(afterReject.status()).isEqualTo("REJECTED");
        assertThat(afterReject.steps()).extracting(SagaExecution.SagaStep::stepName)
            .containsExactly("ORDER_CREATED", "INVENTORY_REJECTED");
        SagaExecution.SagaStep rejectStep = afterReject.steps().get(1);
        assertThat(rejectStep.status()).isEqualTo("FAILED");
        assertThat(rejectStep.beforeState().redis().stockQuantity()).isEqualTo(100);
        assertThat(rejectStep.afterState().redis().stockQuantity()).isEqualTo(100);
        assertThat(rejectStep.beforeState().database().rowCount()).isEqualTo(0);
        assertThat(rejectStep.afterState().database().rowCount()).isEqualTo(0);

        // 3. ORDER_CANCELLED arrives from OrderSagaOrchestrator -> terminal REJECTED without bogus COMPENSATING_INVENTORY
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CANCELLED","orderId":"%s","reason":"Inventory rejected: Insufficient stock for PROD-TECH-001: requested 999, available 100"}
            """.formatted(orderId), "order-events", orderId, 3L);

        SagaExecution afterCancel = sagaService.getSagaById(sagaId).orElseThrow();
        assertThat(afterCancel.status()).isEqualTo("REJECTED");
        assertThat(afterCancel.customerId()).isEqualTo("customer-overstock-test");
        assertThat(afterCancel.steps()).extracting(SagaExecution.SagaStep::stepName)
            .containsExactly("ORDER_CREATED", "INVENTORY_REJECTED", "ORDER_CANCELLED");

        // Verify architectural dossier exists for INVENTORY_REJECTED
        ResponseEntity<ArchitecturalDossier> dossierResp = controller.getStepDossier("INVENTORY_REJECTED");
        assertThat(dossierResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(dossierResp.getBody()).isNotNull();
        assertThat(dossierResp.getBody().patternName()).contains("Stock Boundary Guard");
    }

    @Test
    @DisplayName("Should track real INVENTORY_RESERVED and INVENTORY_RELEASED deltas from inventory-events")
    void shouldTrackRealInventoryReserveAndReleaseDeltas() {
        String orderId = "reserve-" + java.util.UUID.randomUUID();
        String sagaId = "saga-" + orderId;

        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-live-1","totalAmount":149.00}
            """.formatted(orderId), "order-events", orderId, 10L);

        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_RESERVED","orderId":"%s","customerId":"cust-live-1","productId":"PROD-TECH-001","requestedQuantity":1,"stockBefore":100,"stockAfter":99,"reservedBefore":0,"reservedAfter":1}
            """.formatted(orderId), "inventory-events", orderId, 11L);

        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CANCELLED","orderId":"%s","reason":"Customer requested cancellation"}
            """.formatted(orderId), "order-events", orderId, 12L);

        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_RELEASED","orderId":"%s","productId":"PROD-TECH-001","releasedQuantity":1,"stockBefore":99,"stockAfter":100,"reservedBefore":1,"reservedAfter":0}
            """.formatted(orderId), "inventory-events", orderId, 13L);

        SagaExecution saga = sagaService.getSagaById(sagaId).orElseThrow();
        assertThat(saga.status()).isEqualTo("COMPENSATED");
        assertThat(saga.customerId()).isEqualTo("cust-live-1");
        assertThat(saga.steps()).extracting(SagaExecution.SagaStep::stepName)
            .containsExactly("ORDER_CREATED", "INVENTORY_RESERVED", "ORDER_CANCELLED", "COMPENSATING_INVENTORY");

        SagaExecution.SagaStep reserveStep = saga.steps().get(1);
        assertThat(reserveStep.beforeState().redis().stockQuantity()).isEqualTo(100);
        assertThat(reserveStep.afterState().redis().stockQuantity()).isEqualTo(99);
        assertThat(reserveStep.afterState().database().rowCount()).isEqualTo(1);

        SagaExecution.SagaStep compStep = saga.steps().get(3);
        assertThat(compStep.beforeState().redis().stockQuantity()).isEqualTo(99);
        assertThat(compStep.afterState().redis().stockQuantity()).isEqualTo(100);
        assertThat(compStep.afterState().database().rowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should only report AWS S3 diff highlights when an object is added or removed")
    void shouldReportAwsS3DiffOnlyOnAddOrRemove() {
        var db = new InfrastructureSnapshot.DatabaseState("order_db", "orders", 1, "PENDING", "OUTBOX_PUBLISHED");
        var redis = new InfrastructureSnapshot.RedisState(100, false, "lock:stock:PROD-TECH-001", 0);
        var kafka = new InfrastructureSnapshot.KafkaState("order-events", 5, "ord-1");

        // 1. Unchanged S3 (e.g. 2 -> 2 objects, neither found for this order)
        var unchangedBefore = new InfrastructureSnapshot(db, redis, kafka,
            new InfrastructureSnapshot.AwsState("order-invoices-bucket", 2, false, null));
        var unchangedAfter = new InfrastructureSnapshot(db, redis, kafka,
            new InfrastructureSnapshot.AwsState("order-invoices-bucket", 2, false, null));
        InfrastructureDiff noChangeDiff = infraInspector.computeDiff(unchangedBefore, unchangedAfter, "INVENTORY_REJECTED");
        assertThat(noChangeDiff.deltaDetails()).doesNotContainKey("aws.s3_invoice");

        // 2. Object added to S3 (2 -> 3)
        var addedAfter = new InfrastructureSnapshot(db, redis, kafka,
            new InfrastructureSnapshot.AwsState("order-invoices-bucket", 3, true, "invoices/ord-1.json"));
        InfrastructureDiff addedDiff = infraInspector.computeDiff(unchangedBefore, addedAfter, "INVOICE_GENERATED");
        assertThat(addedDiff.deltaDetails().get("aws.s3_invoice")).isEqualTo("Added object: invoices/ord-1.json");
        assertThat(addedDiff.highlights()).anyMatch(h -> h.contains("Added invoices/ord-1.json (+1 object stored)"));

        // 3. Object removed from S3 (3 -> 2)
        InfrastructureDiff removedDiff = infraInspector.computeDiff(addedAfter, unchangedAfter, "INVOICE_DELETED");
        assertThat(removedDiff.deltaDetails().get("aws.s3_invoice")).isEqualTo("Removed object: invoices/ord-1.json");
        assertThat(removedDiff.highlights()).anyMatch(h -> h.contains("Removed invoices/ord-1.json (-1 object)"));
    }

    @Test
    @DisplayName("Should keep all 4 workflows causally consistent with accurate topics and reconciled stock")
    void shouldMaintainCausalConsistencyAcrossHappyPathAndPaymentDecline() {
        // Happy Path: ORDER_CREATED -> INVENTORY_RESERVED -> PAYMENT_CAPTURED -> ORDER_CONFIRMED -> INVENTORY_COMMITTED -> INVOICE_GENERATED -> ORDER_COMPLETED
        String happyOrderId = "happy-" + java.util.UUID.randomUUID();
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-happy","totalAmount":149.00}
            """.formatted(happyOrderId), "order-events", happyOrderId, 100L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_RESERVED","orderId":"%s","customerId":"cust-happy","productId":"PROD-TECH-001","requestedQuantity":1,"stockBefore":98,"stockAfter":97,"reservedBefore":2,"reservedAfter":3}
            """.formatted(happyOrderId), "inventory-events", happyOrderId, 101L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"orderId":"%s","customerId":"cust-happy","amount":149.00,"transactionReference":"TXN_SIM_123"}
            """.formatted(happyOrderId), "payment-events", happyOrderId, 102L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CONFIRMED","orderId":"%s","customerId":"cust-happy","status":"CONFIRMED","totalAmount":149.00}
            """.formatted(happyOrderId), "order-events", happyOrderId, 103L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_COMMITTED","orderId":"%s","productId":"PROD-TECH-001","committedQuantity":1,"stockBefore":97,"stockAfter":97,"reservedBefore":3,"reservedAfter":2}
            """.formatted(happyOrderId), "inventory-events", happyOrderId, 104L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_COMPLETED","orderId":"%s","customerId":"cust-happy","status":"COMPLETED","totalAmount":149.00}
            """.formatted(happyOrderId), "order-events", happyOrderId, 105L);

        SagaExecution happySaga = sagaService.getSagaById("saga-" + happyOrderId).orElseThrow();
        assertThat(happySaga.status()).isEqualTo("COMPLETED");
        assertThat(happySaga.steps()).extracting(SagaExecution.SagaStep::stepName)
            .containsExactly("ORDER_CREATED", "INVENTORY_RESERVED", "PAYMENT_CAPTURED", "ORDER_CONFIRMED", "INVENTORY_COMMITTED", "INVOICE_GENERATED", "ORDER_COMPLETED");
        // ORDER_CREATED causally reconciled to 98 -> 98 (matching INVENTORY_RESERVED's stockBefore=98)
        assertThat(happySaga.steps().get(0).beforeState().redis().stockQuantity()).isEqualTo(98);
        assertThat(happySaga.steps().get(0).afterState().redis().stockQuantity()).isEqualTo(98);
        assertThat(happySaga.steps().get(1).beforeState().redis().stockQuantity()).isEqualTo(98);
        assertThat(happySaga.steps().get(1).afterState().redis().stockQuantity()).isEqualTo(97);
        // ORDER_CONFIRMED carries topic order-events (not payment-events)
        assertThat(happySaga.steps().get(3).afterState().kafka().topic()).isEqualTo("order-events");
        assertThat(happySaga.steps().get(3).afterState().database().latestRecordStatus()).isEqualTo("CONFIRMED");
        // INVENTORY_COMMITTED clears the reserved hold (3 -> 2) while keeping available at 97
        assertThat(happySaga.steps().get(4).beforeState().database().rowCount()).isEqualTo(3);
        assertThat(happySaga.steps().get(4).afterState().database().rowCount()).isEqualTo(2);
        assertThat(happySaga.steps().get(4).afterState().redis().stockQuantity()).isEqualTo(97);
        // ORDER_COMPLETED transitions order_db CONFIRMED -> COMPLETED
        assertThat(happySaga.steps().get(6).beforeState().database().latestRecordStatus()).isEqualTo("CONFIRMED");
        assertThat(happySaga.steps().get(6).afterState().database().latestRecordStatus()).isEqualTo("COMPLETED");

        // Payment Decline: ORDER_CREATED -> INVENTORY_RESERVED -> PAYMENT_DECLINED -> ORDER_CANCELLED -> COMPENSATING_INVENTORY
        String declineOrderId = "decline-" + java.util.UUID.randomUUID();
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-decline","totalAmount":99.99}
            """.formatted(declineOrderId), "order-events", declineOrderId, 200L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_RESERVED","orderId":"%s","customerId":"cust-decline","productId":"PROD-TECH-001","requestedQuantity":1,"stockBefore":97,"stockAfter":96,"reservedBefore":3,"reservedAfter":4}
            """.formatted(declineOrderId), "inventory-events", declineOrderId, 201L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"orderId":"%s","customerId":"cust-decline","amount":99.99,"failureReason":"Insufficient funds"}
            """.formatted(declineOrderId), "payment-events", declineOrderId, 202L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CANCELLED","orderId":"%s","reason":"Payment failed: Insufficient funds"}
            """.formatted(declineOrderId), "order-events", declineOrderId, 203L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"INVENTORY_RELEASED","orderId":"%s","productId":"PROD-TECH-001","releasedQuantity":1,"stockBefore":96,"stockAfter":97,"reservedBefore":4,"reservedAfter":3}
            """.formatted(declineOrderId), "inventory-events", declineOrderId, 204L);

        SagaExecution declineSaga = sagaService.getSagaById("saga-" + declineOrderId).orElseThrow();
        assertThat(declineSaga.status()).isEqualTo("COMPENSATED");
        assertThat(declineSaga.steps()).extracting(SagaExecution.SagaStep::stepName)
            .containsExactly("ORDER_CREATED", "INVENTORY_RESERVED", "PAYMENT_DECLINED", "ORDER_CANCELLED", "COMPENSATING_INVENTORY");
        // ORDER_CANCELLED carries topic order-events and does NOT claim a stock mutation
        assertThat(declineSaga.steps().get(3).afterState().kafka().topic()).isEqualTo("order-events");
        assertThat(declineSaga.steps().get(3).beforeState().redis().stockQuantity())
            .isEqualTo(declineSaga.steps().get(3).afterState().redis().stockQuantity());
        // COMPENSATING_INVENTORY carries the real 96 -> 97 stock restoration
        assertThat(declineSaga.steps().get(4).beforeState().redis().stockQuantity()).isEqualTo(96);
        assertThat(declineSaga.steps().get(4).afterState().redis().stockQuantity()).isEqualTo(97);
    }

    @Test
    @DisplayName("Should search Kafka events by correlation key, broker offset, and event type with real partition/offset alignment")
    void shouldSearchKafkaEventsByCorrelationKeyAndOffset() {
        String searchOrderId = "kafka-search-" + java.util.UUID.randomUUID();
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_COMPLETED","orderId":"%s","customerId":"cust-search","status":"COMPLETED","totalAmount":149.00}
            """.formatted(searchOrderId), "order-events", searchOrderId, 4242L);

        // 1. Search by Correlation Key (orderId)
        ResponseEntity<List<EventRecord>> byKey = controller.getEvents("order-events", null, searchOrderId, 50);
        assertThat(byKey.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(byKey.getBody()).isNotNull().hasSize(1);
        EventRecord found = byKey.getBody().getFirst();
        assertThat(found.correlationId()).isEqualTo(searchOrderId);
        assertThat(found.eventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(found.partition()).isEqualTo(0);
        assertThat(found.offset()).isEqualTo(4242L);

        // 2. Search by broker offset
        ResponseEntity<List<EventRecord>> byOffset = controller.getEvents("order-events", null, "4242", 50);
        assertThat(byOffset.getBody()).isNotNull().anyMatch(e -> searchOrderId.equals(e.correlationId()) && e.offset() == 4242L);

        // 3. Saga step Kafka offset matches the exact broker offset shown in Kafka Explorer
        SagaExecution saga = sagaService.getSagaById("saga-" + searchOrderId).orElseThrow();
        assertThat(saga.steps().getFirst().afterState().kafka().partitionOffset()).isEqualTo(4242L);
    }

    @Test
    @DisplayName("Should normalize all Kafka Event Types to standardized SCREAMING_SNAKE_CASE")
    void shouldStandardizeAllKafkaEventTypes() {
        String orderId = "std-" + java.util.UUID.randomUUID();
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"orderId":"%s","customerId":"cust-std","totalAmount":149.00}
            """.formatted(orderId), "order-events", orderId, 5001L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"PaymentCapturedEvent","orderId":"%s","customerId":"cust-std","amount":149.00,"transactionReference":"TXN_STD"}
            """.formatted(orderId), "payment-events", orderId, 5002L);
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"PaymentFailedEvent","orderId":"%s","customerId":"cust-std","amount":99.99,"failureReason":"Declined"}
            """.formatted(orderId), "payment-events", orderId, 5003L);

        List<EventRecord> recorded = controller.getEvents(null, null, orderId, 50).getBody();
        assertThat(recorded).isNotNull();
        assertThat(recorded).extracting(EventRecord::eventType)
            .containsExactlyInAnyOrder("ORDER_CREATED", "PAYMENT_CAPTURED", "PAYMENT_FAILED");
    }

    @Test
    @DisplayName("Should clear sagas, Kafka event explorer, and invoke backend resets on platform reset")
    void shouldResetPlatformStateCleanly() {
        String orderId = "reset-" + java.util.UUID.randomUUID();
        liveKafkaSagaConsumer.onLiveClusterEvent("""
            {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-reset","totalAmount":49.00}
            """.formatted(orderId), "order-events", orderId, 6001L);

        assertThat(sagaService.getAllSagas()).isNotEmpty();
        assertThat(eventService.getEvents(null, null, 50)).isNotEmpty();

        ResponseEntity<java.util.Map<String, Object>> resp = controller.resetPlatform();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("success")).isEqualTo(true);

        assertThat(sagaService.getAllSagas()).isEmpty();
        assertThat(eventService.getEvents(null, null, 50)).isEmpty();
    }

    @Test
    @DisplayName("Should return complete learning pattern catalog where all code references physically exist on disk")
    void shouldProvideCompleteLearningCenterCatalogWithValidPathsOnDisk() {
        ResponseEntity<List<LearningPattern>> response = controller.getAllLearningPatterns();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<LearningPattern> patterns = response.getBody();
        assertThat(patterns).isNotNull().hasSizeGreaterThanOrEqualTo(19);

        List<String> patternIds = patterns.stream().map(LearningPattern::id).toList();
        assertThat(patternIds).contains(
            "pattern-saga-orchestrator",
            "pattern-cqrs",
            "pattern-transactional-outbox",
            "pattern-api-gateway",
            "pattern-kafka-eventing",
            "pattern-service-registry",
            "pattern-distributed-lock",
            "pattern-virtual-threads",
            "pattern-idempotent-consumer",
            "pattern-flyway-migrations",
            "pattern-cloud-emulator",
            "pattern-keycloak-oauth2",
            "pattern-distributed-observability",
            "pattern-rfc7807-problem-detail",
            "pattern-circuit-breaker-resilience",
            "pattern-dlq-redrive",
            "pattern-archunit-guardrails",
            "pattern-terraform-localstack-iac",
            "pattern-umbrella-helm"
        );

        java.nio.file.Path repoRoot = editorLauncherService.getRepoRoot();
        assertThat(java.nio.file.Files.isDirectory(repoRoot)).isTrue();

        for (LearningPattern pattern : patterns) {
            assertThat(pattern.title()).isNotBlank();
            assertThat(pattern.category()).isNotBlank();
            assertThat(pattern.problem()).isNotBlank();
            assertThat(pattern.concept()).isNotBlank();
            assertThat(pattern.implementation()).isNotBlank();
            assertThat(pattern.services()).isNotEmpty();
            assertThat(pattern.codeReferences())
                .as("Pattern %s must have structured code references", pattern.id())
                .isNotEmpty();

            for (CodeReference ref : pattern.codeReferences()) {
                assertThat(ref.label()).isNotBlank();
                assertThat(ref.relativePath()).isNotBlank();
                assertThat(ref.line()).isGreaterThan(0);

                java.nio.file.Path filePath = repoRoot.resolve(ref.relativePath()).normalize();
                assertThat(java.nio.file.Files.isRegularFile(filePath))
                    .as("Code reference path must exist on disk: %s", ref.relativePath())
                    .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Should launch editor for valid relative repository path")
    void shouldLaunchEditorForValidRelativePath() {
        String relativePath = "services/order-service/src/main/java/com/ravi/order/saga/orchestration/OrderSagaOrchestrator.java";
        EditorLaunchRequest request = new EditorLaunchRequest(relativePath, 31);

        ResponseEntity<EditorLaunchResponse> response = controller.openEditor(request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        EditorLaunchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("SUCCESS");
        assertThat(body.editor()).isIn("VS_CODE", "NOTEPAD");
        assertThat(body.filePath()).isEqualTo(relativePath);
        assertThat(body.absolutePath()).contains("OrderSagaOrchestrator.java");
        assertThat(body.clientLaunchUrl()).startsWith("vscode://file/").endsWith(":31");
        assertThat(body.message()).contains(body.editor().equals("VS_CODE") ? "VS Code" : "Notepad");
    }

    @Test
    @DisplayName("Should block directory traversal attempts in editor launch with 400 Bad Request")
    void shouldBlockDirectoryTraversalInEditorLaunch() {
        List<String> maliciousPaths = List.of(
            "../../Windows/System32/drivers/etc/hosts",
            "../services/order-service/src/main/java/com/ravi/order/OrderServiceApplication.java",
            "services/../../../../etc/passwd",
            "services/order-service/\0/malicious.java"
        );

        for (String badPath : maliciousPaths) {
            EditorLaunchRequest request = new EditorLaunchRequest(badPath, 1);
            System.out.println(">>> EDITOR STATUS: " + editorLauncherService.getEditorStatus());
        ResponseEntity<EditorLaunchResponse> response = controller.openEditor(request);
            assertThat(response.getStatusCode().value())
                .as("Path '%s' must be rejected with 400 Bad Request", badPath)
                .isEqualTo(400);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo("ERROR");
        }
    }

    @Test
    @DisplayName("Should return 404 Not Found for non-existent file in editor launch")
    void shouldReturnNotFoundForNonExistentFile() {
        EditorLaunchRequest request = new EditorLaunchRequest("services/order-service/src/main/java/NonExistentClass.java", 10);
        System.out.println(">>> EDITOR STATUS: " + editorLauncherService.getEditorStatus());
        ResponseEntity<EditorLaunchResponse> response = controller.openEditor(request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Should expose editor detection status with host editor availability")
    void shouldExposeEditorStatusAndDetectHostEditors() {
        ResponseEntity<java.util.Map<String, Object>> response = controller.getEditorStatus();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        java.util.Map<String, Object> status = response.getBody();
        assertThat(status).isNotNull();
        assertThat(status).containsKeys("vsCodeAvailable", "notepadAvailable", "detectedEditor", "repoRoot");
        assertThat(status.get("notepadAvailable")).isEqualTo(true);
        assertThat((String) status.get("detectedEditor")).isIn("VS_CODE", "NOTEPAD");
    }

    @Test
    @DisplayName("Should verify that Control Center HTML provides font size scaling controls, persistence, and shortcuts")
    void testControlCenterHtmlContainsFontSizeAdjustmentControls() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        assertThat(resource.exists()).isTrue();

        String html = resource.getContentAsString(StandardCharsets.UTF_8);

        // Header controls and buttons
        assertThat(html).contains("id=\"btn-font-dec\"");
        assertThat(html).contains("id=\"font-scale-display\"");
        assertThat(html).contains("id=\"btn-font-inc\"");
        assertThat(html).contains("class=\"font-scale-group\"");

        // JavaScript scaling engine functions & configuration
        assertThat(html).contains("const MIN_FONT_SCALE = 80;");
        assertThat(html).contains("const MAX_FONT_SCALE = 150;");
        assertThat(html).contains("const FONT_SCALE_STEP = 10;");
        assertThat(html).contains("const DEFAULT_FONT_SCALE = 100;");
        assertThat(html).contains("const STORAGE_KEY_FONT_SCALE = 'cc_font_scale';");
        assertThat(html).contains("function applyFontScale(");
        assertThat(html).contains("function adjustFontScale(");
        assertThat(html).contains("function resetFontScale(");
        assertThat(html).contains("function initFontScale(");

        // Persistence & shortcut hooks
        assertThat(html).contains("localStorage.getItem(STORAGE_KEY_FONT_SCALE)");
        assertThat(html).contains("localStorage.setItem(STORAGE_KEY_FONT_SCALE");
        assertThat(html).contains("altKey");
        assertThat(html).contains("initFontScale();");
    }

    @Test
    @DisplayName("Should verify that Live Request Flow and Operations Console are merged with synchronized transaction journey")
    void testMergedOperationsConsoleAndDistributedTransactionJourney() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        assertThat(resource.exists()).isTrue();

        String html = resource.getContentAsString(StandardCharsets.UTF_8);

        // Sidebar should have consolidated Operations Console and no separate request-flow item
        assertThat(html).doesNotContain("<li class=\"nav-item\" onclick=\"switchTab('request-flow')\">");
        assertThat(html).contains("onclick=\"switchTab('operations')\"");

        // Standalone view-request-flow is removed, unified inside view-operations
        assertThat(html).doesNotContain("<div id=\"view-request-flow\"");
        assertThat(html).contains("<div id=\"view-operations\"");

        // Distributed Transaction Journey Pipeline embedded inside Operations Console
        assertThat(html).contains("id=\"journey-pipeline-section\"");
        assertThat(html).contains("Distributed Transaction Journey (Order Placement &amp; Saga Execution)");
        assertThat(html).contains("id=\"journey-lifecycle-badge\"");
        assertThat(html).contains("id=\"btn-toggle-journey\"");
        assertThat(html).contains("resetJourneyPipeline()");

        // 5 Connected Stepper Nodes
        assertThat(html).contains("id=\"journey-node-1\"");
        assertThat(html).contains("id=\"journey-node-2\"");
        assertThat(html).contains("id=\"journey-node-3\"");
        assertThat(html).contains("id=\"journey-node-4\"");
        assertThat(html).contains("id=\"journey-node-5\"");

        // Synchronization JavaScript functions
        assertThat(html).contains("function toggleJourneyPipeline()");
        assertThat(html).contains("function resetJourneyPipeline()");
        assertThat(html).contains("function updateJourneyFlowState(");
        assertThat(html).contains("function updateJourneyFlowResult(");
        assertThat(html).contains("function updateJourneyCustomDispatch(");

        // Backward compatibility alias in switchTab
        assertThat(html).contains("if (tabName === 'request-flow')");
    }

    @Test
    @DisplayName("Should verify that Architecture Learning Center prevents viewport clipping with min-height: 0, bottom clearance, and decoupled drawer")
    void testLearningCenterScrollingAndViewportClearance() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        assertThat(resource.exists()).isTrue();

        String html = resource.getContentAsString(StandardCharsets.UTF_8);

        // 1. Flexbox min-height: 0 rules on main-container and view-content to prevent content overflow clipping
        assertThat(html).containsPattern("#main-container\\s*\\{[^}]*min-height:\\s*0;");
        assertThat(html).containsPattern("\\.view-content\\s*\\{[^}]*min-height:\\s*0;");

        // 2. Learning grid bottom margin / clearance
        assertThat(html).containsPattern("\\.learning-grid\\s*\\{[^}]*margin-bottom:\\s*\\d+px;");

        // 3. Learning footer with end-of-catalog status and Back to Top button
        assertThat(html).contains("id=\"learning-footer\"");
        assertThat(html).contains("id=\"learning-footer-count\"");
        assertThat(html).contains("class=\"learning-back-to-top-btn\"");
        assertThat(html).contains("scrollToTopLearning()");
        assertThat(html).contains("function scrollToTopLearning()");

        // 4. Modal inspection drawer is decoupled outside of the scrollable view-learning div
        int viewLearningEnd = html.indexOf("</div>\n\n    <!-- Pattern Inspection Slide-out Drawer");
        if (viewLearningEnd == -1) {
            viewLearningEnd = html.indexOf("</div>\r\n\r\n    <!-- Pattern Inspection Slide-out Drawer");
        }
        assertThat(viewLearningEnd).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should expose Swagger and OpenAPI documentation URLs for microservices in Service Registry")
    void shouldExposeSwaggerAndOpenApiUrlsInServiceRegistry() {
        ResponseEntity<List<ServiceNode>> response = controller.getAllServices();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<ServiceNode> nodes = response.getBody();
        assertThat(nodes).isNotNull();

        List<String> coreServiceIds = List.of(
            "api-gateway", "order-service", "product-service",
            "inventory-service", "payment-service", "notification-service"
        );

        for (String serviceId : coreServiceIds) {
            ServiceNode node = nodes.stream()
                .filter(n -> serviceId.equals(n.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service not found in registry: " + serviceId));

            assertThat(node.swaggerUrl())
                .as("Swagger URL for %s must not be null", serviceId)
                .isNotBlank()
                .contains("swagger-ui");

            assertThat(node.openApiUrl())
                .as("OpenAPI URL for %s must not be null", serviceId)
                .isNotBlank()
                .matches(".*(v3/api-docs|openapi\\.json).*");
        }
    }

    @Test
    @DisplayName("Should verify that Control Center HTML provides OpenAPI & Swagger Hub navigation, iframe, and quick-links")
    void testControlCenterHtmlContainsSwaggerIntegration() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        assertThat(resource.exists()).isTrue();

        String html = resource.getContentAsString(StandardCharsets.UTF_8);

        // Sidebar and toolbar Swagger triggers
        assertThat(html).contains("onclick=\"switchTab('swagger')\"");
        assertThat(html).contains("id=\"nav-swagger\"");
        assertThat(html).contains("id=\"btn-toolbar-swagger\"");

        // OpenAPI & Swagger dedicated view container and iframe
        assertThat(html).contains("id=\"view-swagger\"");
        assertThat(html).contains("id=\"swagger-frame\"");
        assertThat(html).contains("id=\"swagger-service-select\"");

        // JavaScript functions for service switcher and reloading
        assertThat(html).contains("function switchSwaggerService(");
        assertThat(html).contains("function reloadSwaggerFrame(");
        assertThat(html).contains("function openSwaggerServiceDirect(");
    }
}
