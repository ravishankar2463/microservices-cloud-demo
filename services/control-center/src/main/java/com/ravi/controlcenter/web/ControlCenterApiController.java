package com.ravi.controlcenter.web;

import com.ravi.controlcenter.model.*;
import com.ravi.controlcenter.service.*;
import com.ravi.controlcenter.service.LiveOperationsDispatcherService.FlowExecutionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class ControlCenterApiController {

    private final ServiceRegistryService registryService;
    private final ArchitectureTopologyService topologyService;
    private final KafkaEventExplorerService eventService;
    private final SagaExplorerService sagaService;
    private final LearningCenterService learningService;
    private final LiveOperationsDispatcherService liveDispatcherService;
    private final InfrastructureAdminService infraService;
    private final AwsEmulatorService awsEmulatorService;
    private final ArchitecturalPatternCatalogService patternCatalogService;

    public ControlCenterApiController(
            ServiceRegistryService registryService,
            ArchitectureTopologyService topologyService,
            KafkaEventExplorerService eventService,
            SagaExplorerService sagaService,
            LearningCenterService learningService,
            LiveOperationsDispatcherService liveDispatcherService,
            InfrastructureAdminService infraService,
            AwsEmulatorService awsEmulatorService,
            ArchitecturalPatternCatalogService patternCatalogService) {
        this.registryService = registryService;
        this.topologyService = topologyService;
        this.eventService = eventService;
        this.sagaService = sagaService;
        this.learningService = learningService;
        this.liveDispatcherService = liveDispatcherService;
        this.infraService = infraService;
        this.awsEmulatorService = awsEmulatorService;
        this.patternCatalogService = patternCatalogService;
    }

    @GetMapping("/overview")
    public ResponseEntity<SystemOverview> getSystemOverview() {
        List<ServiceNode> nodes = registryService.discoverAllServices();
        int total = nodes.size();
        int running = (int) nodes.stream().filter(n -> "UP".equalsIgnoreCase(n.status())).count();
        int unhealthy = total - running;

        double avgResponse = nodes.stream()
            .filter(n -> n.responseTimeMs() > 0)
            .mapToLong(ServiceNode::responseTimeMs)
            .average()
            .orElse(0.0);

        List<String> alerts = new ArrayList<>();
        if (unhealthy > 0) {
            alerts.add(unhealthy + " microservice(s) currently DOWN or unreachable on expected ports.");
        }

        String healthStatus = unhealthy == 0 ? "HEALTHY" : (running > 0 ? "DEGRADED" : "CRITICAL");

        SystemOverview overview = new SystemOverview(
            total,
            running,
            unhealthy,
            0.0,
            Math.round(avgResponse * 10.0) / 10.0,
            eventService.getTopics().size(),
            healthStatus,
            "LOCAL_DEVELOPMENT",
            alerts,
            Instant.now()
        );

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/services")
    public ResponseEntity<List<ServiceNode>> getAllServices() {
        return ResponseEntity.ok(registryService.discoverAllServices());
    }

    @GetMapping("/services/{serviceId}")
    public ResponseEntity<ServiceNode> getServiceById(@PathVariable String serviceId) {
        return registryService.getServiceById(serviceId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/architecture")
    public ResponseEntity<ArchitectureTopology> getArchitectureTopology() {
        return ResponseEntity.ok(topologyService.getTopology());
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventRecord>> getEvents(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(eventService.getEvents(topic, status, limit));
    }

    @GetMapping("/kafka/topics")
    public ResponseEntity<List<String>> getTopics() {
        return ResponseEntity.ok(eventService.getTopics());
    }

    @GetMapping("/sagas")
    public ResponseEntity<List<SagaExecution>> getSagas() {
        return ResponseEntity.ok(sagaService.getAllSagas());
    }

    @GetMapping("/sagas/{sagaId}")
    public ResponseEntity<SagaExecution> getSagaById(@PathVariable String sagaId) {
        return sagaService.getSagaById(sagaId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sagas/dossier/{stepName}")
    public ResponseEntity<ArchitecturalDossier> getStepDossier(@PathVariable String stepName) {
        return ResponseEntity.ok(patternCatalogService.getDossierForStep(stepName));
    }

    @GetMapping("/learning")
    public ResponseEntity<List<LearningPattern>> getAllLearningPatterns() {
        return ResponseEntity.ok(learningService.getAllPatterns());
    }

    @GetMapping("/learning/{patternId}")
    public ResponseEntity<LearningPattern> getLearningPatternById(@PathVariable String patternId) {
        return learningService.getPatternById(patternId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/live/dispatch")
    public ResponseEntity<LiveDispatchResponse> dispatchLiveRequest(@RequestBody LiveDispatchRequest request) {
        return ResponseEntity.ok(liveDispatcherService.dispatch(request));
    }

    @GetMapping("/live/presets")
    public ResponseEntity<List<LiveFlowPreset>> getLivePresets() {
        return ResponseEntity.ok(liveDispatcherService.getPresets());
    }

    @PostMapping("/live/execute-flow")
    public ResponseEntity<FlowExecutionResult> executeLiveFlow(
            @RequestParam(defaultValue = "happy-path") String presetId,
            @RequestBody(required = false) Map<String, Object> overrides) {
        FlowExecutionResult result = liveDispatcherService.executeFlow(presetId, overrides);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/live/clear-sagas")
    public ResponseEntity<Map<String, String>> clearSagas() {
        sagaService.clearAll();
        return ResponseEntity.ok(Map.of("message", "All saga records cleared successfully"));
    }

    @PostMapping("/operations/refresh")
    public ResponseEntity<List<ServiceNode>> refreshServices() {
        return ResponseEntity.ok(registryService.discoverAllServices());
    }

    @GetMapping("/infra/nodes")
    public ResponseEntity<List<InfraNodeInfo>> getInfraNodes(@RequestParam(required = false) String platform) {
        return ResponseEntity.ok(infraService.getNodes(platform));
    }

    @GetMapping("/infra/logs")
    public ResponseEntity<Map<String, String>> getInfraLogs(
            @RequestParam(required = false) String platform,
            @RequestParam String name,
            @RequestParam(defaultValue = "100") int tail) {
        String logs = infraService.getLogs(platform, name, tail);
        return ResponseEntity.ok(Map.of("name", name, "logs", logs));
    }

    @GetMapping("/infra/diagnostics")
    public ResponseEntity<Map<String, String>> getInfraDiagnostics(
            @RequestParam(required = false) String platform,
            @RequestParam String name) {
        String diag = infraService.getDiagnostics(platform, name);
        return ResponseEntity.ok(Map.of("name", name, "diagnostics", diag));
    }

    @GetMapping("/aws-emulator")
    public ResponseEntity<AwsEmulatorHealth> getAwsEmulatorHealth() {
        return ResponseEntity.ok(awsEmulatorService.getHealth());
    }
}
