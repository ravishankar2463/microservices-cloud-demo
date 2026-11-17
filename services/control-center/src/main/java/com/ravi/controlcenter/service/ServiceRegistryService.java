package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.ServiceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ServiceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistryService.class);

    private final RestClient restClient;
    private final Map<String, ServiceDefinition> serviceDefinitions = new LinkedHashMap<>();

    private record ServiceDefinition(
        String id,
        String name,
        String type,
        int port,
        String probeUrl,
        String healthPath,
        String publicUrl,
        String description,
        String database,
        List<String> topicsConsumed,
        List<String> topicsProduced,
        List<String> dependencies
    ) {}

    public ServiceRegistryService(
            @Value("${services.api-gateway.url:http://localhost:8080}") String gatewayUrl,
            @Value("${services.order-service.url:http://localhost:8081}") String orderUrl,
            @Value("${services.inventory-service.url:http://localhost:8082}") String inventoryUrl,
            @Value("${services.notification-service.url:http://localhost:8083}") String notificationUrl,
            @Value("${services.payment-service.url:http://localhost:8084}") String paymentUrl,
            @Value("${services.product-service.url:http://localhost:8085}") String productUrl,
            @Value("${services.keycloak.url:http://localhost:8088}") String keycloakUrl,
            @Value("${services.aws-emulator.url:http://localhost:4566}") String awsEmulatorUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(4000));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        registerServices(gatewayUrl, orderUrl, inventoryUrl, notificationUrl, paymentUrl, productUrl, keycloakUrl, awsEmulatorUrl);
    }

    private void registerServices(String gatewayUrl, String orderUrl, String inventoryUrl, String notificationUrl,
                                  String paymentUrl, String productUrl, String keycloakUrl, String awsEmulatorUrl) {
        serviceDefinitions.put("api-gateway", new ServiceDefinition(
            "api-gateway", "API Gateway", "MICROSERVICE", 8080, gatewayUrl, "/actuator/health", "http://localhost:8080",
            "Central edge gateway, Token-Bucket rate limiting, X-Correlation-ID tracing, JWT validation",
            "None", List.of(), List.of(), List.of("order-service", "inventory-service", "product-service", "payment-service", "keycloak")
        ));
        serviceDefinitions.put("order-service", new ServiceDefinition(
            "order-service", "Order Service", "MICROSERVICE", 8081, orderUrl, "/actuator/health", "http://localhost:8081",
            "Saga Orchestrator, CQRS Write/Read models, Transactional Outbox pattern",
            "order_db (PostgreSQL)", List.of("payment-events"), List.of("order-events"), List.of("kafka", "order-db", "redis", "aws-emulator")
        ));
        serviceDefinitions.put("inventory-service", new ServiceDefinition(
            "inventory-service", "Inventory Service", "MICROSERVICE", 8082, inventoryUrl, "/actuator/health", "http://localhost:8082",
            "Redis distributed lock (SET NX PX), Java 21 Virtual Threads (Loom), Saga compensation stock release",
            "inventory-redis (In-Memory/Cache)", List.of("order-events"), List.of("inventory-events"), List.of("redis", "kafka")
        ));
        serviceDefinitions.put("notification-service", new ServiceDefinition(
            "notification-service", "Notification Service", "MICROSERVICE", 8083, notificationUrl, "/actuator/health", "http://localhost:8083",
            "Idempotent event consumer filter, LocalStack S3 invoice document archival",
            "aws-emulator (S3/DynamoDB)", List.of("order-events"), List.of(), List.of("kafka", "aws-emulator")
        ));
        serviceDefinitions.put("payment-service", new ServiceDefinition(
            "payment-service", "Payment Service", "MICROSERVICE", 8084, paymentUrl, "/actuator/health", "http://localhost:8084",
            "Deterministic payment simulator (.99 decline trigger, .50 timeout), strict idempotency",
            "payment_db (PostgreSQL)", List.of("payment-requests"), List.of("payment-events"), List.of("payment-db", "kafka")
        ));
        serviceDefinitions.put("product-service", new ServiceDefinition(
            "product-service", "Product Service", "MICROSERVICE", 8085, productUrl, "/actuator/health", "http://localhost:8085",
            "Catalog aggregate root, category-based queries, Flyway schema migrations",
            "product_db (PostgreSQL)", List.of(), List.of(), List.of("product-db")
        ));
        serviceDefinitions.put("keycloak", new ServiceDefinition(
            "keycloak", "Keycloak IAM", "IAM", 8088, keycloakUrl, "/realms/master", "http://localhost:8088",
            "Centralized OAuth2 / OIDC Identity Provider with pre-seeded ecommerce realm",
            "Embedded/H2", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("aws-emulator", new ServiceDefinition(
            "aws-emulator", "AWS Emulator (LocalStack)", "CLOUD_EMULATOR", 4566, awsEmulatorUrl, "/_localstack/health", "http://localhost:4566/_localstack/health",
            "Zero-cost local AWS cloud emulator for SQS, SNS, S3, and DynamoDB",
            "LocalStack Storage", List.of(), List.of(), List.of()
        ));
    }

    public List<ServiceNode> discoverAllServices() {
        List<ServiceNode> nodes = new CopyOnWriteArrayList<>();

        // Query services in parallel using Java 21 Virtual Threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = serviceDefinitions.values().stream()
                .map(def -> CompletableFuture.runAsync(() -> {
                    long start = System.currentTimeMillis();
                    String status = "DOWN";
                    long responseTime = -1;

                    try {
                        String healthUrl = def.probeUrl() + def.healthPath();
                        String response = restClient.get()
                                .uri(healthUrl)
                                .retrieve()
                                .body(String.class);
                        if (response != null && (response.contains("\"status\":\"DOWN\"") || response.contains("\"DOWN\"") || response.contains("\"OUT_OF_SERVICE\""))) {
                            status = "DOWN";
                        } else if (response != null && (response.contains("\"UP\"") || response.contains("\"status\":\"UP\"") || response.contains("ready") || response.contains("\"available\"") || response.contains("\"realm\""))) {
                            status = "UP";
                        } else {
                            status = "UNKNOWN";
                        }
                        responseTime = System.currentTimeMillis() - start;
                    } catch (Exception e) {
                        // Service is down or not currently running in local env
                        status = "DOWN";
                        responseTime = -1;
                    }

                    nodes.add(new ServiceNode(
                        def.id(),
                        def.name(),
                        def.type(),
                        def.port(),
                        def.publicUrl(),
                        status,
                        responseTime,
                        def.description(),
                        def.database(),
                        def.topicsConsumed(),
                        def.topicsProduced(),
                        def.dependencies()
                    ));
                }, executor))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // Sort by port order
        return nodes.stream().sorted(Comparator.comparingInt(ServiceNode::port)).toList();
    }

    public Optional<ServiceNode> getServiceById(String serviceId) {
        return discoverAllServices().stream()
                .filter(s -> s.id().equalsIgnoreCase(serviceId))
                .findFirst();
    }
}
