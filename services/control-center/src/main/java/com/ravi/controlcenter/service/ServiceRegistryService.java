package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.ServiceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
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
        boolean tcpProbe,
        String publicUrl,
        String description,
        String database,
        List<String> topicsConsumed,
        List<String> topicsProduced,
        List<String> dependencies,
        String swaggerUrl,
        String openApiUrl
    ) {
        ServiceDefinition(
            String id, String name, String type, int port, String probeUrl, String healthPath,
            boolean tcpProbe, String publicUrl, String description, String database,
            List<String> topicsConsumed, List<String> topicsProduced, List<String> dependencies
        ) {
            this(id, name, type, port, probeUrl, healthPath, tcpProbe, publicUrl, description, database,
                 topicsConsumed, topicsProduced, dependencies, null, null);
        }
    }

    public ServiceRegistryService(
            @Value("${services.api-gateway.url:http://localhost:8080}") String gatewayUrl,
            @Value("${services.order-service.url:http://localhost:8081}") String orderUrl,
            @Value("${services.inventory-service.url:http://localhost:8082}") String inventoryUrl,
            @Value("${services.notification-service.url:http://localhost:8083}") String notificationUrl,
            @Value("${services.payment-service.url:http://localhost:8084}") String paymentUrl,
            @Value("${services.product-service.url:http://localhost:8085}") String productUrl,
            @Value("${services.keycloak.url:http://localhost:8088}") String keycloakUrl,
            @Value("${services.aws-emulator.url:http://localhost:4566}") String awsEmulatorUrl,
            @Value("${services.kafka.address:${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}}") String kafkaAddress,
            @Value("${services.redis.address:localhost:6379}") String redisAddress,
            @Value("${services.order-db.address:localhost:5432}") String orderDbAddress,
            @Value("${services.product-db.address:localhost:5433}") String productDbAddress,
            @Value("${services.payment-db.address:localhost:5434}") String paymentDbAddress,
            @Value("${services.prometheus.url:http://localhost:9090}") String prometheusUrl,
            @Value("${services.tempo.url:http://localhost:3200}") String tempoUrl,
            @Value("${services.grafana.url:http://localhost:3000}") String grafanaUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(4000));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        registerServices(
            gatewayUrl, orderUrl, inventoryUrl, notificationUrl, paymentUrl, productUrl, keycloakUrl, awsEmulatorUrl,
            kafkaAddress, redisAddress, orderDbAddress, productDbAddress, paymentDbAddress,
            prometheusUrl, tempoUrl, grafanaUrl
        );
    }

    private void registerServices(
            String gatewayUrl, String orderUrl, String inventoryUrl, String notificationUrl,
            String paymentUrl, String productUrl, String keycloakUrl, String awsEmulatorUrl,
            String kafkaAddress, String redisAddress, String orderDbAddress, String productDbAddress, String paymentDbAddress,
            String prometheusUrl, String tempoUrl, String grafanaUrl) {
        serviceDefinitions.put("api-gateway", new ServiceDefinition(
            "api-gateway", "API Gateway", "MICROSERVICE", 8080, gatewayUrl, "/actuator/health", false, "http://localhost:8080",
            "Central edge gateway, Token-Bucket rate limiting, X-Correlation-ID tracing, JWT validation",
            "None", List.of(), List.of(), List.of("order-service", "inventory-service", "product-service", "payment-service", "keycloak"),
            "http://localhost:8080/swagger-ui.html", "http://localhost:8080/v3/api-docs"
        ));
        serviceDefinitions.put("order-service", new ServiceDefinition(
            "order-service", "Order Service", "MICROSERVICE", 8081, orderUrl, "/actuator/health", false, "http://localhost:8081",
            "Saga Orchestrator, CQRS Write/Read models, Transactional Outbox pattern",
            "order_db (PostgreSQL)", List.of("payment-events", "inventory-events"), List.of("order-events"), List.of("kafka", "order-db", "inventory-redis", "aws-emulator"),
            "http://localhost:8080/swagger-ui.html?urls.primaryName=Order%20Service", "http://localhost:8080/services/order-service/openapi.json"
        ));
        serviceDefinitions.put("inventory-service", new ServiceDefinition(
            "inventory-service", "Inventory Service", "MICROSERVICE", 8082, inventoryUrl, "/actuator/health", false, "http://localhost:8082",
            "Redis distributed lock (SET NX PX), Java 21 Virtual Threads (Loom), Reserve -> Commit / Release lifecycle",
            "inventory-redis (In-Memory/Cache)", List.of("order-events"), List.of("inventory-events"), List.of("inventory-redis", "kafka"),
            "http://localhost:8080/swagger-ui.html?urls.primaryName=Inventory%20Service", "http://localhost:8080/services/inventory-service/openapi.json"
        ));
        serviceDefinitions.put("notification-service", new ServiceDefinition(
            "notification-service", "Notification Service", "MICROSERVICE", 8083, notificationUrl, "/actuator/health", false, "http://localhost:8083",
            "Idempotent event consumer filter, LocalStack S3 invoice document archival",
            "aws-emulator (S3/DynamoDB)", List.of("order-events"), List.of(), List.of("kafka", "aws-emulator"),
            "http://localhost:8080/swagger-ui.html?urls.primaryName=Notification%20Service", "http://localhost:8080/services/notification-service/openapi.json"
        ));
        serviceDefinitions.put("payment-service", new ServiceDefinition(
            "payment-service", "Payment Service", "MICROSERVICE", 8084, paymentUrl, "/actuator/health", false, "http://localhost:8084",
            "Deterministic payment simulator (.99 decline trigger, .50 timeout), strict idempotency",
            "payment_db (PostgreSQL)", List.of("payment-requests"), List.of("payment-events"), List.of("payment-db", "kafka"),
            "http://localhost:8080/swagger-ui.html?urls.primaryName=Payment%20Service", "http://localhost:8080/services/payment-service/openapi.json"
        ));
        serviceDefinitions.put("product-service", new ServiceDefinition(
            "product-service", "Product Service", "MICROSERVICE", 8085, productUrl, "/actuator/health", false, "http://localhost:8085",
            "Catalog aggregate root, category-based queries, Flyway schema migrations",
            "product_db (PostgreSQL)", List.of(), List.of(), List.of("product-db"),
            "http://localhost:8080/swagger-ui.html?urls.primaryName=Product%20Service", "http://localhost:8080/services/product-service/openapi.json"
        ));
        serviceDefinitions.put("keycloak", new ServiceDefinition(
            "keycloak", "Keycloak IAM", "IAM", 8088, keycloakUrl, "/realms/master", false, "http://localhost:8088",
            "Centralized OAuth2 / OIDC Identity Provider with pre-seeded ecommerce realm",
            "Embedded/H2", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("aws-emulator", new ServiceDefinition(
            "aws-emulator", "AWS Emulator (LocalStack)", "CLOUD_EMULATOR", 4566, awsEmulatorUrl, "/_localstack/health", false, "http://localhost:4566/_localstack/health",
            "Zero-cost local AWS cloud emulator for SQS, SNS, S3, and DynamoDB",
            "LocalStack Storage", List.of(), List.of(), List.of()
        ));

        // Core Platform Infrastructure (live TCP health probed)
        serviceDefinitions.put("kafka", new ServiceDefinition(
            "kafka", "Apache Kafka", "INFRA", 9092, kafkaAddress, "", true, "localhost:9092",
            "Distributed event streaming broker in KRaft mode (order-events, inventory-events, payment-events)",
            "KRaft Event Broker", List.of(), List.of("order-events", "inventory-events", "payment-events", "order-events-dlq"), List.of()
        ));
        serviceDefinitions.put("inventory-redis", new ServiceDefinition(
            "inventory-redis", "Redis Cache", "INFRA", 6379, redisAddress, "", true, "localhost:6379",
            "In-memory Redis server for distributed mutex locks (SET NX PX) and CQRS read-through caching",
            "Redis 7 (In-Memory)", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("order-db", new ServiceDefinition(
            "order-db", "PostgreSQL (Order DB)", "INFRA", 5432, orderDbAddress, "", true, "localhost:5432",
            "Dedicated PostgreSQL relational database for orders, order_items, and transactional outbox",
            "PostgreSQL 16 (order_db)", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("product-db", new ServiceDefinition(
            "product-db", "PostgreSQL (Product DB)", "INFRA", 5433, productDbAddress, "", true, "localhost:5433",
            "Dedicated PostgreSQL relational database for product catalog and categories",
            "PostgreSQL 16 (product_db)", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("payment-db", new ServiceDefinition(
            "payment-db", "PostgreSQL (Payment DB)", "INFRA", 5434, paymentDbAddress, "", true, "localhost:5434",
            "Dedicated PostgreSQL relational database for payments ledger and idempotency keys",
            "PostgreSQL 16 (payment_db)", List.of(), List.of(), List.of()
        ));

        // Distributed Observability Infrastructure
        serviceDefinitions.put("prometheus", new ServiceDefinition(
            "prometheus", "Prometheus Metrics", "OBSERVABILITY", 9090, prometheusUrl, "/-/healthy", false, "http://localhost:9090",
            "Time-series metrics engine scraping /actuator/prometheus endpoints across all microservices",
            "Prometheus TSDB", List.of(), List.of(), List.of("api-gateway", "order-service", "inventory-service", "payment-service", "product-service", "notification-service")
        ));
        serviceDefinitions.put("tempo", new ServiceDefinition(
            "tempo", "Grafana Tempo", "OBSERVABILITY", 3200, tempoUrl, "/ready", false, "http://localhost:3200",
            "Distributed tracing backend collecting W3C trace context spans via OpenTelemetry OTLP",
            "Tempo Block Storage", List.of(), List.of(), List.of()
        ));
        serviceDefinitions.put("grafana", new ServiceDefinition(
            "grafana", "Grafana Dashboards", "OBSERVABILITY", 3000, grafanaUrl, "/api/health", false, "http://localhost:3000",
            "Unified visualization console for microservice RED metrics and distributed trace waterfalls",
            "Grafana Internal", List.of(), List.of(), List.of("prometheus", "tempo")
        ));
    }

    public List<ServiceNode> discoverAllServices() {
        List<ServiceNode> nodes = new CopyOnWriteArrayList<>();

        // Query services and infrastructure in parallel using Java 21 Virtual Threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = serviceDefinitions.values().stream()
                .map(def -> CompletableFuture.runAsync(() -> {
                    long start = System.currentTimeMillis();
                    String status = "DOWN";
                    long responseTime = -1;

                    if (def.tcpProbe()) {
                        try {
                            String firstEndpoint = def.probeUrl().split(",")[0].trim();
                            String[] hostPort = firstEndpoint.split(":");
                            String host = hostPort[0];
                            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : def.port();
                            try (Socket socket = new Socket()) {
                                socket.connect(new InetSocketAddress(host, port), 1500);
                                status = "UP";
                                responseTime = System.currentTimeMillis() - start;
                            }
                        } catch (Exception e) {
                            status = "DOWN";
                            responseTime = -1;
                        }
                    } else {
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
                            status = "DOWN";
                            responseTime = -1;
                        }
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
                        def.dependencies(),
                        def.swaggerUrl(),
                        def.openApiUrl()
                    ));
                }, executor))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // Sort: Microservices & Cloud/IAM first, then INFRA nodes by port
        return nodes.stream()
            .sorted(Comparator
                .comparingInt((ServiceNode n) -> "INFRA".equals(n.type()) ? 1 : 0)
                .thenComparingInt(ServiceNode::port))
            .toList();
    }

    public Optional<ServiceNode> getServiceById(String serviceId) {
        return discoverAllServices().stream()
                .filter(s -> s.id().equalsIgnoreCase(serviceId))
                .findFirst();
    }
}
