package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.LearningPattern;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LearningCenterService {

    private final List<LearningPattern> patterns = List.of(
        new LearningPattern(
            "pattern-api-gateway",
            "API Gateway & Edge Security Pattern",
            "Architecture",
            "Clients should not couple to dozens of internal microservice addresses, ports, authentication schemes, or rate limits.",
            "A single entry point reverse proxy managing SSL termination, JWT authentication, rate limiting, and request routing.",
            "Spring Cloud Gateway (MVC) route definitions with Token-Bucket rate limiting filter and Keycloak JWT validation.",
            List.of("api-gateway", "keycloak"),
            "api-gateway/.../filter/RateLimitingFilter.java, SecurityConfig.java",
            "Pros: Unified security boundary, centralized audit, protocol translation. Cons: Potential single point of failure if unscaled, slight edge latency overhead."
        ),
        new LearningPattern(
            "pattern-cqrs",
            "Command Query Responsibility Segregation (CQRS)",
            "Data Architecture",
            "Complex join queries on relational write models degrade OLTP performance and introduce contention on transaction tables.",
            "Separate the write model (optimizing commands, ACID validation, business rules) from read projections (denormalized, cached reads).",
            "Order Service maintains Order write aggregate in PostgreSQL and async caches denormalized OrderView in Redis.",
            List.of("order-service"),
            "order-service/.../domain/Order.java, .../service/OrderQueryService.java",
            "Pros: Blazing fast read throughput, independent scaling of reads vs writes. Cons: Eventual consistency delay between write commit and read projection."
        ),
        new LearningPattern(
            "pattern-transactional-outbox",
            "Transactional Outbox Pattern",
            "Reliability",
            "Directly publishing to Kafka inside a database transaction leads to dual-write inconsistencies if the broker fails or commits fail.",
            "Write the domain event to an Outbox table in the SAME ACID database transaction as the business entity, then poll/stream to Kafka.",
            "Order Service writes to `outbox_events` table during order placement. A scheduled relay publishes uncommitted events to Kafka and SQS.",
            List.of("order-service"),
            "order-service/.../outbox/OutboxEvent.java, .../outbox/OutboxPublisher.java",
            "Pros: Guaranteed At-Least-Once event delivery, prevents ghost events or lost state. Cons: Increased write storage and small polling latency."
        ),
        new LearningPattern(
            "pattern-saga-orchestrator",
            "Saga Orchestration Pattern (Compensating Transactions)",
            "Distributed Transactions",
            "Two-phase commit (2PC) does not scale across microservices with independent databases and locks resources across network boundaries.",
            "An orchestrator coordinates a series of local transactions across services. If any step fails, compensating transactions undo earlier steps in reverse.",
            "OrderSagaOrchestrator tracks Order -> Inventory Reserved -> Payment Processing. If payment fails, it emits compensation to release reserved inventory.",
            List.of("order-service", "inventory-service", "payment-service"),
            "order-service/.../saga/OrderSagaOrchestrator.java, inventory-service/.../service/InventoryService.java",
            "Pros: Non-blocking distributed consistency, fault isolation. Cons: Eventual consistency complexity, compensating transactions must be idempotent."
        ),
        new LearningPattern(
            "pattern-distributed-lock",
            "Redis Distributed Lock (SET NX PX)",
            "Concurrency",
            "Concurrent stock reservations across multiple inventory service replicas cause race conditions and negative inventory.",
            "Atomic distributed mutex using Redis `SET lock:key token NX PX milliseconds` ensuring only one thread/node modifies stock at any instant.",
            "Inventory Service acquires lock before decrementing stock and releases it cleanly in a finally block.",
            List.of("inventory-service"),
            "inventory-service/.../service/InventoryService.java",
            "Pros: Millisecond lock acquisition, avoids cross-node data corruption. Cons: Needs fail-safe lock TTL to avoid deadlocks on node crash."
        ),
        new LearningPattern(
            "pattern-virtual-threads",
            "Java 21 Virtual Threads (Project Loom)",
            "Performance & Concurrency",
            "Traditional OS platform threads consume ~1MB stack memory each, exhausting system resources under thousands of concurrent I/O requests.",
            "Lightweight user-mode threads scheduled by the JVM on carrier threads. When blocking on I/O, the carrier thread is unmounted to run other work.",
            "Inventory Service and Control Center run concurrent health probes and lock calls using `Executors.newVirtualThreadPerTaskExecutor()`.",
            List.of("inventory-service", "control-center"),
            "inventory-service/.../config/AsyncConfig.java, control-center/.../ServiceRegistryService.java",
            "Pros: Massive concurrent throughput with simple synchronous coding style. Cons: Carrier thread pinning if calling synchronized blocks or JNI."
        ),
        new LearningPattern(
            "pattern-idempotent-consumer",
            "Idempotent Event Consumer Pattern",
            "Messaging",
            "Kafka guarantees At-Least-Once delivery, meaning duplicate events can be delivered during network hiccups or consumer rebalances.",
            "The consumer records processed message IDs in a deduplication store. Duplicate messages are detected and safely skipped.",
            "Notification Service tracks processed event IDs before generating PDF invoices and archiving them into LocalStack S3.",
            List.of("notification-service"),
            "notification-service/.../consumer/OrderEventConsumer.java",
            "Pros: Eliminates duplicate charges, duplicate emails, and corrupted data. Cons: Storage overhead for deduplication ledger and TTL cleanup."
        ),
        new LearningPattern(
            "pattern-flyway-migrations",
            "Database-per-Service & Flyway Schema Migrations",
            "DevOps & Persistence",
            "Shared databases create tight coupling, blocking independent deployments and causing schema drift across environments.",
            "Each microservice owns its private database. Schema changes are version-controlled with immutable SQL migration scripts.",
            "Order Service, Product Service, and Payment Service execute Flyway V1/V2 scripts on startup with `ddl-auto: validate`.",
            List.of("order-service", "product-service", "payment-service"),
            "services/*/src/main/resources/db/migration/V1__*.sql",
            "Pros: Clean microservice boundaries, reproducible zero-downtime migrations. Cons: Cross-service joins must be handled via APIs or event projections."
        ),
        new LearningPattern(
            "pattern-cloud-emulator",
            "Zero-Cost Local AWS Cloud Emulator (LocalStack)",
            "Cloud Native",
            "Testing against live AWS clouds costs money, requires credentials, risk accidental charges, and complicates developer onboarding.",
            "An open-source local emulator simulating SQS, SNS, S3, and DynamoDB on localhost:4566 with zero credentials and zero cloud cost.",
            "Notification Service creates S3 buckets and archives invoices. Order Service publishes to SQS queues alongside Kafka.",
            List.of("order-service", "notification-service"),
            "docker/docker-compose.yml, .../aws/AwsClientConfig.java",
            "Pros: 100% offline development, instant teardown and re-creation, zero billing risk. Cons: Minor API parity differences for obscure AWS services."
        ),
        new LearningPattern(
            "pattern-keycloak-oauth2",
            "Centralized Identity & RBAC with Keycloak",
            "Security",
            "Hardcoding credentials or writing custom authentication per microservice causes security vulnerabilities and fractured user management.",
            "Centralized OpenID Connect (OIDC) identity provider. Gateway verifies JWT signatures and propagates user ID and roles via downstream headers.",
            "Keycloak 25 container pre-seeded with `ecommerce-realm.json`, `customer1` (ROLE_CUSTOMER), and `admin1` (ROLE_ADMIN).",
            List.of("api-gateway", "keycloak"),
            "docker/keycloak/ecommerce-realm.json, api-gateway/.../KeycloakJwtAuthenticationConverter.java",
            "Pros: Standardized OAuth2/OIDC, SSO capability, centralized token revocation. Cons: Keycloak startup overhead in local test environments."
        )
    );

    public List<LearningPattern> getAllPatterns() {
        return patterns;
    }

    public Optional<LearningPattern> getPatternById(String id) {
        return patterns.stream()
            .filter(p -> p.id().equalsIgnoreCase(id))
            .findFirst();
    }
}
