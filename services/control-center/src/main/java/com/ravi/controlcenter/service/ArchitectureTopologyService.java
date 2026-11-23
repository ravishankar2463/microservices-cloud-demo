package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.ArchitectureTopology;
import com.ravi.controlcenter.model.ServiceNode;
import com.ravi.controlcenter.model.TopologyEdge;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArchitectureTopologyService {

    private final ServiceRegistryService registryService;

    public ArchitectureTopologyService(ServiceRegistryService registryService) {
        this.registryService = registryService;
    }

    public ArchitectureTopology getTopology() {
        List<ServiceNode> allRegistered = registryService.discoverAllServices();
        Map<String, ServiceNode> byId = allRegistered.stream()
            .collect(Collectors.toMap(ServiceNode::id, Function.identity(), (a, b) -> a));

        List<ServiceNode> nodes = new ArrayList<>(
            allRegistered.stream().filter(n -> !"INFRA".equals(n.type())).toList()
        );

        // Map live-probed INFRA nodes into topology categories (BROKER, CACHE, DATABASE) without duplication
        ServiceNode kafkaLive = byId.get("kafka");
        ServiceNode redisLive = byId.get("inventory-redis");
        ServiceNode orderDbLive = byId.get("order-db");
        ServiceNode productDbLive = byId.get("product-db");
        ServiceNode paymentDbLive = byId.get("payment-db");

        nodes.add(new ServiceNode(
            "kafka", "Apache Kafka", "BROKER", 9092, "localhost:9092",
            kafkaLive != null ? kafkaLive.status() : "DOWN",
            kafkaLive != null ? kafkaLive.responseTimeMs() : -1,
            "KRaft Event Streaming Backbone for Event-Driven Architecture",
            "None", List.of(), List.of("order-events", "payment-events", "inventory-events"), List.of()
        ));
        nodes.add(new ServiceNode(
            "redis", "Redis Cache & Locks", "CACHE", 6379, "localhost:6379",
            redisLive != null ? redisLive.status() : "DOWN",
            redisLive != null ? redisLive.responseTimeMs() : -1,
            "Sub-millisecond Distributed Concurrency Locking & CQRS Cache-Aside",
            "Redis 7.2 Alpine", List.of(), List.of(), List.of()
        ));
        nodes.add(new ServiceNode(
            "order-db", "Order DB (PostgreSQL)", "DATABASE", 5432, "localhost:5432",
            orderDbLive != null ? orderDbLive.status() : "DOWN",
            orderDbLive != null ? orderDbLive.responseTimeMs() : -1,
            "Isolated transactional store for orders, items, and transactional outbox",
            "PostgreSQL 16", List.of(), List.of(), List.of()
        ));
        nodes.add(new ServiceNode(
            "product-db", "Product DB (PostgreSQL)", "DATABASE", 5433, "localhost:5433",
            productDbLive != null ? productDbLive.status() : "DOWN",
            productDbLive != null ? productDbLive.responseTimeMs() : -1,
            "Catalog relational store with Flyway schema versioning",
            "PostgreSQL 16", List.of(), List.of(), List.of()
        ));
        nodes.add(new ServiceNode(
            "payment-db", "Payment DB (PostgreSQL)", "DATABASE", 5434, "localhost:5434",
            paymentDbLive != null ? paymentDbLive.status() : "DOWN",
            paymentDbLive != null ? paymentDbLive.responseTimeMs() : -1,
            "Payment ledger with idempotency keys and simulated gateway audit",
            "PostgreSQL 16", List.of(), List.of(), List.of()
        ));

        List<TopologyEdge> edges = List.of(
            // Gateway Inbound & Security
            new TopologyEdge("e1", "api-gateway", "keycloak", "SYNC_REST", "JWKS Certs & Auth Validation", false),
            new TopologyEdge("e2", "api-gateway", "product-service", "SYNC_REST", "GET /api/products (Public)", false),
            new TopologyEdge("e3", "api-gateway", "order-service", "SYNC_REST", "POST /api/orders (Customer JWT)", true),
            new TopologyEdge("e4", "api-gateway", "inventory-service", "SYNC_REST", "GET /api/inventory", false),
            new TopologyEdge("e5", "api-gateway", "payment-service", "SYNC_REST", "GET /api/payments", false),

            // Order Service Outbound & Persistence
            new TopologyEdge("e6", "order-service", "order-db", "PERSISTENCE", "Orders & Outbox ACID Table", false),
            new TopologyEdge("e7", "order-service", "redis", "CACHE", "CQRS Read Projections (OrderView)", false),
            new TopologyEdge("e8", "order-service", "kafka", "ASYNC_KAFKA", "Outbox Event: order-events", true),
            new TopologyEdge("e9", "order-service", "aws-emulator", "CLOUD_INTEGRATION", "SQS order-events-queue", true),

            // Inventory Service
            new TopologyEdge("e10", "kafka", "inventory-service", "ASYNC_KAFKA", "Listen order-events -> auto-reserve", true),
            new TopologyEdge("e11", "inventory-service", "redis", "CACHE", "SET lock:stock:{sku} NX PX", true),

            // Payment Service
            new TopologyEdge("e12", "payment-service", "payment-db", "PERSISTENCE", "Payment Aggregate & Idempotency", false),
            new TopologyEdge("e13", "payment-service", "kafka", "ASYNC_KAFKA", "Publish: payment-events", true),

            // Saga Orchestration loop
            new TopologyEdge("e14", "kafka", "order-service", "ASYNC_KAFKA", "Saga Callback: payment-events", true),

            // Notification Service & Local S3
            new TopologyEdge("e15", "kafka", "notification-service", "ASYNC_KAFKA", "Consume: order-events (Idempotent)", true),
            new TopologyEdge("e16", "notification-service", "aws-emulator", "CLOUD_INTEGRATION", "Upload Invoice -> S3 Bucket", true),

            // Product Catalog
            new TopologyEdge("e17", "product-service", "product-db", "PERSISTENCE", "Flyway V1 Schema, V2 Seed", false)
        );

        return new ArchitectureTopology(nodes, edges);
    }
}
