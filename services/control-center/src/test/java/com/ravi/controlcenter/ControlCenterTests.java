package com.ravi.controlcenter;

import com.ravi.controlcenter.model.*;
import com.ravi.controlcenter.service.*;
import com.ravi.controlcenter.service.LiveOperationsDispatcherService.FlowExecutionResult;
import com.ravi.controlcenter.web.ControlCenterApiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

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
        assertThat(overview.totalServices()).isEqualTo(8);
        assertThat(overview.kafkaTopicsCount()).isGreaterThanOrEqualTo(4);
        assertThat(overview.environment()).isEqualTo("LOCAL_DEVELOPMENT");
    }

    @Test
    @DisplayName("Should discover all 8 microservices and infrastructure nodes in service registry")
    void shouldDiscoverAllServices() {
        ResponseEntity<List<ServiceNode>> response = controller.getAllServices();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<ServiceNode> nodes = response.getBody();
        assertThat(nodes).isNotNull().hasSize(8);

        List<String> serviceIds = nodes.stream().map(ServiceNode::id).toList();
        assertThat(serviceIds).contains(
            "api-gateway",
            "order-service",
            "inventory-service",
            "notification-service",
            "payment-service",
            "product-service",
            "keycloak",
            "aws-emulator"
        );
    }

    @Test
    @DisplayName("Should return architecture topology with nodes and communication edges")
    void shouldReturnArchitectureTopology() {
        ResponseEntity<ArchitectureTopology> response = controller.getArchitectureTopology();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArchitectureTopology topology = response.getBody();
        assertThat(topology).isNotNull();
        assertThat(topology.nodes()).hasSize(13); // 8 services + 5 infra (kafka, redis, 3 dbs)
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
}
