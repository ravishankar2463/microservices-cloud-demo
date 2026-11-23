package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.CodeReference;
import com.ravi.controlcenter.model.LearningPattern;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service providing the architectural learning center catalog.
 * Maps all core microservices patterns across the demo to concrete source code implementations,
 * exact file paths, line numbers, and architectural trade-offs.
 */
@Service
public class LearningCenterService {

    private final List<LearningPattern> patterns = List.of(
        new LearningPattern(
            "pattern-saga-orchestrator",
            "Saga Orchestration Pattern (Compensating Transactions)",
            "Distributed Transactions",
            "Two-phase commit (2PC) does not scale across microservices with independent databases and locks resources across network boundaries.",
            "An orchestrator coordinates a series of local transactions across services. If any step fails (e.g. inventory depletion or payment decline), compensating transactions undo earlier steps in reverse order.",
            "OrderSagaOrchestrator tracks Order -> Inventory Reserved -> Payment Processing -> Order Confirmed -> Stock Committed. If payment or stock reservation fails, it emits compensation commands to release reserved stock and transition order to CANCELLED.",
            List.of("order-service", "inventory-service", "payment-service", "control-center"),
            "services/order-service/.../saga/orchestration/OrderSagaOrchestrator.java:31",
            List.of(
                new CodeReference("Order Saga Orchestrator Listener", "services/order-service/src/main/java/com/ravi/order/saga/orchestration/OrderSagaOrchestrator.java", 31, "Listens to inventory-events and payment-events; coordinates confirm and compensation commands"),
                new CodeReference("Order Command & Compensation Handler", "services/order-service/src/main/java/com/ravi/order/application/command/handler/OrderCommandHandler.java", 98, "Handles CancelOrderCommand to trigger compensating rollback and emit OrderCancelledEvent"),
                new CodeReference("Inventory Reservation & Compensation Listener", "services/inventory-service/src/main/java/com/ravi/inventory/infrastructure/messaging/consumer/OrderEventListener.java", 40, "Listens for ORDER_CREATED (reserve stock) and ORDER_CANCELLED (compensating stock release)"),
                new CodeReference("Payment Processing & Failure Simulation", "services/payment-service/src/main/java/com/ravi/payment/application/service/PaymentService.java", 38, "Executes payment authorization; amounts ending in .99 trigger deliberate decline for saga compensation"),
                new CodeReference("Control Center Live Cluster Saga Consumer", "services/control-center/src/main/java/com/ravi/controlcenter/messaging/LiveKafkaSagaConsumer.java", 74, "Intercepts live cluster Kafka events and dynamically builds distributed saga timelines with state diffs")
            ),
            "Pros: Non-blocking distributed consistency, fault isolation, no distributed locks across network. Cons: Eventual consistency delay, compensating transactions must be idempotent.",
            "sagas"
        ),
        new LearningPattern(
            "pattern-cqrs",
            "Command Query Responsibility Segregation (CQRS)",
            "Data Architecture",
            "Complex join queries on relational write models degrade OLTP performance and introduce lock contention on transactional tables.",
            "Separate the write model (optimizing commands, ACID validation, business rules) from read projections (denormalized, high-throughput cached reads).",
            "Order Service separates writes into OrderCommandHandler (persisting to PostgreSQL) and reads into OrderQueryHandler (implementing Redis Cache-Aside with 10-minute TTL and transactional eviction hooks).",
            List.of("order-service"),
            "services/order-service/.../application/command/handler/OrderCommandHandler.java:56",
            List.of(
                new CodeReference("Order Command Handler (Write Model)", "services/order-service/src/main/java/com/ravi/order/application/command/handler/OrderCommandHandler.java", 56, "Handles CreateOrderCommand, validates domain invariants, and saves normalized OrderJpaEntity to PostgreSQL"),
                new CodeReference("Order Query Handler (Read Model Cache-Aside)", "services/order-service/src/main/java/com/ravi/order/application/query/handler/OrderQueryHandler.java", 36, "Queries Redis cache key order:view:{id} first; falls back to PostgreSQL on miss and repopulates Redis"),
                new CodeReference("Order Command REST Controller", "services/order-service/src/main/java/com/ravi/order/presentation/rest/controller/OrderCommandController.java", 25, "Command ingress endpoint: POST /api/orders"),
                new CodeReference("Order Query REST Controller", "services/order-service/src/main/java/com/ravi/order/presentation/rest/controller/OrderQueryController.java", 26, "Query ingress endpoint: GET /api/orders/{id}"),
                new CodeReference("Normalized Write Entity (PostgreSQL)", "services/order-service/src/main/java/com/ravi/order/infrastructure/persistence/entity/OrderJpaEntity.java", 12, "Relational table mapping for ACID consistency")
            ),
            "Pros: High read throughput, independent scaling of reads vs writes, optimized schemas. Cons: Eventual consistency delay between write commit and cache invalidation.",
            "operations"
        ),
        new LearningPattern(
            "pattern-transactional-outbox",
            "Transactional Outbox Pattern (Dual-Write Prevention)",
            "Reliability & Eventing",
            "Directly publishing to Kafka inside a database transaction leads to dual-write inconsistencies if the message broker fails or commits fail.",
            "Write the domain event to an Outbox table in the SAME ACID database transaction as the business entity, then reliably poll/stream events to Kafka.",
            "Order Service writes to `outbox` table during order creation and cancellation. A scheduled relay (OutboxPublisher) polls pending events every 1000ms and publishes to Kafka with aggregateId as partition key.",
            List.of("order-service"),
            "services/order-service/.../infrastructure/messaging/outbox/OutboxPublisher.java:39",
            List.of(
                new CodeReference("Outbox JPA Entity", "services/order-service/src/main/java/com/ravi/order/infrastructure/messaging/outbox/OutboxJpaEntity.java", 12, "Maps outbox table with aggregateType, aggregateId, payload, and PENDING/PUBLISHED status"),
                new CodeReference("Outbox Scheduled Relay Publisher", "services/order-service/src/main/java/com/ravi/order/infrastructure/messaging/outbox/OutboxPublisher.java", 39, "Fixed-delay poller that streams pending events to Kafka with partition key and marks them PUBLISHED"),
                new CodeReference("Atomic Outbox Save in Command Handler", "services/order-service/src/main/java/com/ravi/order/application/command/handler/OrderCommandHandler.java", 80, "Saves OrderCreatedEvent to outbox table within the @Transactional boundary of CreateOrderCommand"),
                new CodeReference("Outbox Table Schema DDL", "services/order-service/src/main/resources/db/migration/V1__init_order_schema.sql", 24, "PostgreSQL DDL defining outbox table and status indexes")
            ),
            "Pros: Guaranteed At-Least-Once event delivery, prevents ghost events or lost state. Cons: Polling delay (1000ms), storage overhead requiring cleanup of published rows.",
            "kafka"
        ),
        new LearningPattern(
            "pattern-api-gateway",
            "API Gateway & Edge Security Pattern",
            "Edge & Security",
            "Clients should not couple to dozens of internal microservice addresses, ports, authentication schemes, or rate limits.",
            "A single ingress reverse proxy managing SSL termination, Keycloak JWT verification, role-based authorization, rate limiting, and request correlation.",
            "Spring Security stateless filter chain validates Keycloak JWTs, extracts user roles (ROLE_ADMIN, ROLE_CUSTOMER), enforces token-bucket rate limits, and propagates X-Correlation-ID downstream via RestClient.",
            List.of("api-gateway", "keycloak"),
            "services/api-gateway/.../infrastructure/security/SecurityConfig.java:26",
            List.of(
                new CodeReference("Spring Security Filter Chain", "services/api-gateway/src/main/java/com/ravi/gateway/infrastructure/security/SecurityConfig.java", 26, "Stateless JWT resource server configuration with role-based route authorizations"),
                new CodeReference("Keycloak JWT Role Converter", "services/api-gateway/src/main/java/com/ravi/gateway/infrastructure/security/KeycloakJwtAuthenticationConverter.java", 20, "Extracts realm_access.roles and maps them to Spring GrantedAuthority (ROLE_ADMIN, ROLE_CUSTOMER)"),
                new CodeReference("Token-Bucket Rate Limiter", "services/api-gateway/src/main/java/com/ravi/gateway/infrastructure/ratelimit/RateLimiterService.java", 20, "In-memory token bucket enforcing 100 requests per minute with HTTP 429 backoff"),
                new CodeReference("Distributed Correlation ID Filter", "services/api-gateway/src/main/java/com/ravi/gateway/infrastructure/filter/CorrelationIdFilter.java", 18, "Assigns or forwards X-Correlation-ID to SLF4J MDC and downstream request headers"),
                new CodeReference("Gateway Ingress Routing Controller", "services/api-gateway/src/main/java/com/ravi/gateway/presentation/rest/controller/GatewayRoutingController.java", 46, "Dispatches external client requests to internal services via Spring RestClient")
            ),
            "Pros: Centralized security, rate limiting, unified CORS, request telemetry. Cons: Ingress latency hop, potential single point of failure if not scaled horizontally.",
            "topology"
        ),
        new LearningPattern(
            "pattern-kafka-eventing",
            "Event-Driven Architecture with Apache Kafka (KRaft)",
            "Event-Driven Backbone",
            "Synchronous HTTP REST calls between all microservices create cascading latency, temporal coupling, and distributed failures.",
            "Asynchronous choreography via Apache Kafka event streaming. Producers emit domain events into partitioned topics; consumers process them independently.",
            "Multi-topic Kafka event pipeline across order-events, inventory-events, and payment-events with partition key ordering, KRaft consensus, and live telemetry capture.",
            List.of("order-service", "inventory-service", "payment-service", "notification-service", "control-center"),
            "services/control-center/.../service/KafkaEventExplorerService.java:27",
            List.of(
                new CodeReference("Kafka Event Explorer Service", "services/control-center/src/main/java/com/ravi/controlcenter/service/KafkaEventExplorerService.java", 27, "Buffers, filters, and correlates events across all cluster topics for dashboard analysis"),
                new CodeReference("Inventory Event Publisher", "services/inventory-service/src/main/java/com/ravi/inventory/infrastructure/messaging/producer/InventoryEventPublisher.java", 20, "Emits InventoryReserved, InventoryRejected, and InventoryReleased events"),
                new CodeReference("Notification Kafka Consumer", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/messaging/consumer/OrderNotificationKafkaListener.java", 28, "Consumes order events for invoice generation and S3 archival with idempotency checks"),
                new CodeReference("Live Kafka Saga Consumer", "services/control-center/src/main/java/com/ravi/controlcenter/messaging/LiveKafkaSagaConsumer.java", 74, "Real-time cluster consumer tracing offsets, partitions, and saga state transitions")
            ),
            "Pros: Temporal decoupling, backpressure buffering, replayability, high throughput. Cons: Event schema evolution overhead, debugging distributed event flows.",
            "kafka"
        ),
        new LearningPattern(
            "pattern-service-registry",
            "Service Registry & Cluster Topology Discovery",
            "Resilience & Discovery",
            "Hardcoding microservice IP addresses and ports causes deployment fragility and makes cluster health monitoring difficult.",
            "A centralized service registry continuously probing all microservices and infrastructure nodes using non-blocking virtual threads.",
            "ServiceRegistryService maintains registry definitions for 8 application microservices and 5 infrastructure nodes, executing concurrent HTTP /actuator/health and TCP socket probes.",
            List.of("control-center"),
            "services/control-center/.../service/ServiceRegistryService.java:38",
            List.of(
                new CodeReference("Service Registry & Health Prober", "services/control-center/src/main/java/com/ravi/controlcenter/service/ServiceRegistryService.java", 38, "Maintains 13 cluster node definitions and executes concurrent health probes"),
                new CodeReference("Architecture Topology Graph Builder", "services/control-center/src/main/java/com/ravi/controlcenter/service/ArchitectureTopologyService.java", 25, "Constructs communication edges (SYNC_REST, ASYNC_KAFKA, CACHE, PERSISTENCE) between nodes"),
                new CodeReference("Docker Compose Service Definitions", "docker/docker-compose.yml", 1, "Infrastructure and microservice container network topology"),
                new CodeReference("Control Center Application Config", "services/control-center/src/main/resources/application.yml", 1, "Port bindings and environment override endpoints")
            ),
            "Pros: Live cluster visibility, automated latency detection, real-time dependency topology. Cons: Periodic probing network overhead.",
            "overview"
        ),
        new LearningPattern(
            "pattern-distributed-lock",
            "Redis Distributed Lock (Atomic SET NX PX)",
            "Concurrency & Mutex",
            "Concurrent stock reservations across multiple inventory service replicas cause race conditions, overselling, and negative stock.",
            "Atomic distributed mutex using Redis `SET lock:key token NX PX milliseconds` ensuring only one microservice instance can reserve stock at any moment.",
            "Inventory Service acquires a distributed lock in RedisDistributedLock prior to stock modification and releases it via token verification.",
            List.of("inventory-service"),
            "services/inventory-service/.../infrastructure/redis/RedisDistributedLock.java:28",
            List.of(
                new CodeReference("Redis Distributed Lock Implementation", "services/inventory-service/src/main/java/com/ravi/inventory/infrastructure/redis/RedisDistributedLock.java", 28, "Atomic lock acquisition via setIfAbsent with TTL and token-verified release"),
                new CodeReference("Inventory Reservation Service", "services/inventory-service/src/main/java/com/ravi/inventory/application/service/InventoryReservationService.java", 25, "Coordinates stock checks and wraps reservations inside the distributed lock boundary")
            ),
            "Pros: Sub-millisecond mutex acquisition, cross-replica consistency. Cons: Requires fail-safe lock TTL to avoid deadlocks if a node terminates.",
            "operations"
        ),
        new LearningPattern(
            "pattern-virtual-threads",
            "Java 21/25 Virtual Threads (Project Loom)",
            "Performance & Concurrency",
            "Traditional OS platform threads consume ~1MB stack memory each, exhausting system memory and thread pools under thousands of concurrent I/O operations.",
            "Lightweight user-mode threads scheduled by the JVM onto carrier threads. When blocking on network I/O or database queries, the carrier thread is unmounted to execute other tasks.",
            "Control Center and Inventory Service utilize `Executors.newVirtualThreadPerTaskExecutor()` for health checks, and `spring.threads.virtual.enabled: true` handles all web requests.",
            List.of("control-center", "inventory-service"),
            "services/control-center/.../service/ServiceRegistryService.java:144",
            List.of(
                new CodeReference("Virtual Thread Probe Executor", "services/control-center/src/main/java/com/ravi/controlcenter/service/ServiceRegistryService.java", 144, "Spawns virtual threads for parallel HTTP and TCP cluster probes"),
                new CodeReference("Virtual Threads Enabled in Spring Boot", "services/control-center/src/main/resources/application.yml", 17, "spring.threads.virtual.enabled: true configures Tomcat to dispatch on Loom virtual threads")
            ),
            "Pros: Massive concurrent I/O throughput with simple synchronous coding style, negligible memory footprint. Cons: Avoid synchronized blocks or JNI calls that pin carrier threads.",
            "overview"
        ),
        new LearningPattern(
            "pattern-idempotent-consumer",
            "Idempotent Event Consumer Pattern",
            "Messaging & Data Safety",
            "Kafka guarantees At-Least-Once delivery, meaning network retries or consumer group rebalances can cause duplicate event deliveries.",
            "The consumer tracks processed event IDs in an atomic deduplication store. If an event ID is already recorded, the consumer acknowledges and safely discards it.",
            "Notification Service's IdempotentMessageStore checks incoming OrderCreated events before generating PDF invoices or uploading to LocalStack S3.",
            List.of("notification-service"),
            "services/notification-service/.../messaging/inbox/IdempotentMessageStore.java:26",
            List.of(
                new CodeReference("Idempotent Message Store", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/messaging/inbox/IdempotentMessageStore.java", 26, "Atomic deduplication ledger verifying whether an event ID was previously processed"),
                new CodeReference("Order Notification Kafka Listener", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/messaging/consumer/OrderNotificationKafkaListener.java", 28, "Applies idempotency check prior to executing invoice archival")
            ),
            "Pros: Eliminates duplicate invoices, duplicate emails, and corrupted state. Cons: Deduplication ledger requires storage and TTL cleanup.",
            "kafka"
        ),
        new LearningPattern(
            "pattern-flyway-migrations",
            "Database-per-Service & Flyway Schema Migrations",
            "DevOps & Persistence",
            "Shared databases create tight coupling, blocking independent deployments and causing schema drift across microservices.",
            "Each microservice owns its private PostgreSQL database. Schema migrations are version-controlled with immutable SQL scripts executed automatically on boot.",
            "Order Service, Product Service, and Payment Service maintain dedicated PostgreSQL databases with Flyway V1/V2 migration scripts and validate schemas on startup.",
            List.of("order-service", "product-service", "payment-service"),
            "services/order-service/.../db/migration/V1__init_order_schema.sql:1",
            List.of(
                new CodeReference("Order Service Schema Migration", "services/order-service/src/main/resources/db/migration/V1__init_order_schema.sql", 1, "DDL for orders, order_items, and outbox tables"),
                new CodeReference("Product Service Schema Migration", "services/product-service/src/main/resources/db/migration/V1__init_product_schema.sql", 1, "DDL for products catalog table"),
                new CodeReference("Payment Service Schema Migration", "services/payment-service/src/main/resources/db/migration/V1__init_payment_schema.sql", 1, "DDL for payments and transactions table")
            ),
            "Pros: Clean microservice bounded contexts, reproducible zero-downtime database evolution. Cons: Cross-service joins must be handled via APIs or event projections.",
            "overview"
        ),
        new LearningPattern(
            "pattern-cloud-emulator",
            "Zero-Cost Local AWS Cloud Emulator (LocalStack)",
            "Cloud Native",
            "Testing against live AWS clouds costs money, requires credentials, risks accidental billing, and slows developer feedback loops.",
            "An open-source local cloud emulator simulating S3, SQS, SNS, and DynamoDB on localhost:4566 with zero credentials and instant teardown.",
            "Notification Service creates S3 buckets and archives invoices; AwsEmulatorService continuously monitors LocalStack service availability.",
            List.of("notification-service", "control-center"),
            "services/control-center/.../service/AwsEmulatorService.java:30",
            List.of(
                new CodeReference("AWS Emulator Monitoring Service", "services/control-center/src/main/java/com/ravi/controlcenter/service/AwsEmulatorService.java", 30, "Monitors LocalStack service health and parses active AWS services"),
                new CodeReference("AWS SDK v2 S3 Configuration", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/config/AwsS3Config.java", 33, "Configures S3Client targeting http://localhost:4566 with path-style addressing"),
                new CodeReference("LocalStack S3 Invoice Archival Adapter", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/persistence/adapter/S3InvoiceArchiveRepositoryAdapter.java", 25, "Uploads generated invoice PDFs directly into LocalStack S3 bucket")
            ),
            "Pros: 100% offline local development, zero billing risk, fast integration testing. Cons: Minor API parity edge cases compared to real AWS.",
            "overview"
        ),
        new LearningPattern(
            "pattern-keycloak-oauth2",
            "Centralized Identity & RBAC with Keycloak",
            "Security & Auth",
            "Hardcoding credentials or writing custom authentication per microservice causes security vulnerabilities and fractured user management.",
            "Centralized OpenID Connect (OIDC) identity provider. Gateway verifies JWT signatures and propagates verified identities and roles downstream.",
            "Keycloak container pre-seeded with ecommerce realm, client secrets, and test users (admin1 with ROLE_ADMIN, customer1 with ROLE_CUSTOMER).",
            List.of("api-gateway", "keycloak"),
            "docker/keycloak/ecommerce-realm.json:1",
            List.of(
                new CodeReference("Keycloak Realm Pre-Seeded Export", "docker/keycloak/ecommerce-realm.json", 1, "Realm definition, roles, clients, and credentials for local dev"),
                new CodeReference("Keycloak JWT Role Converter", "services/api-gateway/src/main/java/com/ravi/gateway/infrastructure/security/KeycloakJwtAuthenticationConverter.java", 20, "Extracts Keycloak realm and client roles into Spring GrantedAuthority list")
            ),
            "Pros: Standardized OAuth2/OIDC, Single Sign-On (SSO), centralized credential management. Cons: Keycloak container memory overhead in development.",
            "topology"
        ),
        new LearningPattern(
            "pattern-distributed-observability",
            "Distributed Observability & Tracing (OpenTelemetry, Prometheus & Grafana Tempo)",
            "Distributed Observability",
            "In an asynchronous, distributed microservice architecture, diagnosing cascading latency bottlenecks, dropped messages, or failure root causes across independent services without end-to-end trace context is virtually impossible.",
            "Standardized telemetry collection with OpenTelemetry (OTel) and Micrometer Tracing. W3C TraceContext headers ('traceparent', 'tracestate') propagate across HTTP REST hops and Kafka event headers, exporting metrics to Prometheus and distributed spans to Grafana Tempo.",
            "All 7 microservices configure Micrometer Tracing with 100% sampling probability, exporting OTLP traces to Grafana Tempo (localhost:4318) and Prometheus metrics on /actuator/prometheus. Grafana links Prometheus metrics directly to Tempo span waterfalls.",
            List.of("api-gateway", "order-service", "inventory-service", "payment-service", "notification-service", "control-center", "prometheus", "tempo", "grafana"),
            "docker/observability/tempo/tempo.yml:1",
            List.of(
                new CodeReference("Prometheus Scrape Configuration", "docker/observability/prometheus/prometheus.yml", 1, "Scrapes /actuator/prometheus endpoints across all 7 platform microservices"),
                new CodeReference("Grafana Tempo OTLP Tracing Pipeline", "docker/observability/tempo/tempo.yml", 1, "Receives OTLP traces on gRPC :4317 and HTTP :4318, indexes span attributes and trace waterfalls"),
                new CodeReference("Grafana Provisioned Datasources", "docker/observability/grafana/provisioning/datasources/datasources.yml", 1, "Configures Prometheus and Tempo datasources with automatic trace-to-metrics correlation"),
                new CodeReference("Microservice OTLP & Metrics Configuration", "services/control-center/src/main/resources/application.yml", 36, "Configures Micrometer Tracing, 1.0 sampling probability, and OTLP HTTP trace exporter endpoint"),
                new CodeReference("Control Center Observability Node Probing", "services/control-center/src/main/java/com/ravi/controlcenter/service/ServiceRegistryService.java", 145, "Monitors Prometheus (:9090), Tempo (:3200), and Grafana (:3000) health and cluster topology")
            ),
            "Pros: Full end-to-end distributed transaction visibility, microsecond trace waterfalls, zero blind spots across Kafka and REST hops. Cons: Network overhead of exporting spans at 100% sample rate; storage footprint for long-term trace retention.",
            "overview"
        ),
        new LearningPattern(
            "pattern-rfc7807-problem-detail",
            "RFC 7807 Standardized ProblemDetail API Errors",
            "API Design & Error Governance",
            "Inconsistent error payloads across microservices (different keys for message, status, timestamp, or stack traces) force frontend and API clients to write brittle, service-specific error parsing code.",
            "Adopt RFC 7807 'Problem Details for HTTP APIs', the modern IETF and Spring standard for HTTP error responses. Standardizes URI type, title, status code, human-readable detail, instance URI, and contextual metadata.",
            "A GlobalExceptionHandler (@RestControllerAdvice) implemented across all microservices extends ResponseEntityExceptionHandler and handles validation errors, business domain exceptions, constraint violations, and unhandled errors into uniform ProblemDetail envelopes.",
            List.of("api-gateway", "order-service", "inventory-service", "product-service", "payment-service", "control-center"),
            "services/order-service/src/main/java/com/ravi/order/presentation/rest/error/GlobalExceptionHandler.java:21",
            List.of(
                new CodeReference("Order Service RFC 7807 Global Exception Handler", "services/order-service/src/main/java/com/ravi/order/presentation/rest/error/GlobalExceptionHandler.java", 21, "Intercepts validation, domain invariants, and unhandled exceptions, producing uniform ProblemDetail"),
                new CodeReference("API Gateway RFC 7807 Ingress Exception Handler", "services/api-gateway/src/main/java/com/ravi/gateway/presentation/rest/error/GlobalExceptionHandler.java", 22, "Handles routing timeouts, downstream service unreachability, and gateway circuit breaker rejections"),
                new CodeReference("Inventory Service RFC 7807 Exception Handler", "services/inventory-service/src/main/java/com/ravi/inventory/presentation/rest/error/GlobalExceptionHandler.java", 21, "Maps stock depletion, locking timeouts, and optimistic concurrency conflicts to RFC 7807 format"),
                new CodeReference("Control Center RFC 7807 Exception Handler", "services/control-center/src/main/java/com/ravi/controlcenter/presentation/rest/error/GlobalExceptionHandler.java", 21, "Produces standard error envelopes for cluster administrative operations and preset triggers")
            ),
            "Pros: Machine-readable standardized error contract across all microservices, seamless client parsing, eliminates leaking raw internal stack traces. Cons: Requires disciplined domain-specific URI type definitions and consistent mapping.",
            "overview"
        ),
        new LearningPattern(
            "pattern-circuit-breaker-resilience",
            "Microservice Egress Resilience & Circuit Breaker (Resilience4j)",
            "Resilience Engineering",
            "Downstream service latency spikes, network partitions, or hanging third-party external APIs (e.g. issuing banks, payment processors, or catalog lookups) block calling threads, cascading thread pool exhaustion across microservices.",
            "Resilience4j Circuit Breaker and Retry on outbound integration seams. Protects caller services by monitoring failure rates in a sliding window, tripping OPEN to fail fast or execute domain fallbacks without waiting for socket timeouts or exhausting worker threads.",
            "Implemented at microservice integration boundaries: order-service decorates synchronous ProductCatalogClient REST lookups with @CircuitBreaker to fail fast (RFC 7807 HTTP 503) during catalog degradation; payment-service decorates external PaymentProviderPort authorization calls with @CircuitBreaker to gracefully drive Saga compensation.",
            List.of("order-service", "payment-service"),
            "services/order-service/src/main/java/com/ravi/order/infrastructure/client/ProductCatalogClient.java:27",
            List.of(
                new CodeReference("Order Service Product Catalog Circuit Breaker", "services/order-service/src/main/java/com/ravi/order/infrastructure/client/ProductCatalogClient.java", 27, "Guards synchronous REST product-service pre-flight lookups with @CircuitBreaker and @Retry"),
                new CodeReference("Order Service Product Catalog Fallback", "services/order-service/src/main/java/com/ravi/order/infrastructure/client/ProductCatalogClient.java", 42, "Trips when product catalog fails, throwing ProductCatalogUnavailableException to yield RFC 7807 503"),
                new CodeReference("Payment Service External Gateway Circuit Breaker", "services/payment-service/src/main/java/com/ravi/payment/infrastructure/external/SimulatedPaymentProviderAdapter.java", 20, "Guards external banking/payment processor integration with @CircuitBreaker and @Retry"),
                new CodeReference("Payment Service Circuit Breaker Fallback", "services/payment-service/src/main/java/com/ravi/payment/infrastructure/external/SimulatedPaymentProviderAdapter.java", 45, "Returns failed PaymentResult on gateway timeout or open circuit to trigger deterministic Saga rollback"),
                new CodeReference("Order Service Resilience4j Settings", "services/order-service/src/main/resources/application.yaml", 72, "Configures sliding-window-size, failure-rate-threshold (50%), and wait-duration-in-open-state")
            ),
            "Pros: Localized fault containment at the exact network integration boundary, zero cascading failures, cleanly drives domain fallbacks and Saga compensations. Cons: Requires tuning failure rate thresholds and sliding window sizes per dependency.",
            "operations"
        ),
        new LearningPattern(
            "pattern-dlq-redrive",
            "Dead Letter Queue (DLQ) Parking & Redrive Pattern",
            "Reliability & Eventing",
            "Poison-pill messages (corrupted JSON payloads, schema violations, or unhandled null pointers) cause Kafka consumer listener threads to crash and enter infinite retry loops, halting message consumption for all subsequent valid events.",
            "Dead Letter Queue (DLQ) parking and manual/automated redrive. When message processing encounters unrecoverable errors, the poison-pill message is caught, enriched with failure metadata (error message, timestamp, original topic), parked in a DLQ store, and acknowledged to allow the consumer stream to proceed.",
            "Notification Service catches unhandled listener exceptions, parks failed messages in DeadLetterQueueService with reason and payload, and exposes /api/notifications/dlq REST APIs for operators to inspect, redrive (replay back into workflow), or purge dead-lettered messages.",
            List.of("notification-service", "control-center"),
            "services/notification-service/src/main/java/com/ravi/notification/application/service/DeadLetterQueueService.java:19",
            List.of(
                new CodeReference("Dead Letter Queue In-Memory Parking Ledger", "services/notification-service/src/main/java/com/ravi/notification/application/service/DeadLetterQueueService.java", 19, "Thread-safe concurrent parking ledger tracking messageId, payload, failureReason, and timestamp"),
                new CodeReference("Consumer Poison-Pill Quarantine Handler", "services/notification-service/src/main/java/com/ravi/notification/infrastructure/messaging/consumer/OrderNotificationKafkaListener.java", 81, "Catches unhandled event exceptions, parks message in DLQ, and allows partition offset to advance"),
                new CodeReference("DLQ Inspection & Redrive REST Controller", "services/notification-service/src/main/java/com/ravi/notification/presentation/rest/controller/NotificationController.java", 67, "Exposes GET /api/notifications/dlq, POST /api/notifications/dlq/redrive/{id}, and DELETE /api/notifications/dlq")
            ),
            "Pros: Prevents consumer partition head-of-line blocking, preserves poisoned messages for forensics and operator replay. Cons: Requires DLQ monitoring, alert thresholds, and operational runbooks for redrive.",
            "kafka"
        ),
        new LearningPattern(
            "pattern-archunit-guardrails",
            "Architecture Fitness Functions (ArchUnit Guardrails)",
            "Architecture Governance",
            "Over time, microservices codebases decay through architectural drift — engineers accidentally import infrastructure classes into pure domain logic, create cyclic package dependencies, or violate layer boundaries.",
            "Automated Architecture Fitness Functions as Code using ArchUnit. Unit tests analyze JVM bytecode during 'mvn test' to verify hexagonal architecture layer separation, package isolation, naming conventions, and injection safety as automated CI gates.",
            "ArchUnit test suites in all microservices enforce strict hexagonal rules: domain/core cannot depend on presentation/adapters/infrastructure; service beans must reside in service packages; field injection (@Autowired) is forbidden in favor of constructor injection.",
            List.of("control-center", "order-service", "inventory-service", "product-service", "payment-service", "notification-service", "api-gateway"),
            "services/control-center/src/test/java/com/ravi/controlcenter/architecture/ArchitectureRulesTest.java:14",
            List.of(
                new CodeReference("Control Center ArchUnit Architecture Rules", "services/control-center/src/test/java/com/ravi/controlcenter/architecture/ArchitectureRulesTest.java", 14, "Enforces layered architecture, no cyclic dependencies, no field injection, and standard naming conventions"),
                new CodeReference("Order Service Hexagonal Architecture Rules", "services/order-service/src/test/java/com/ravi/order/architecture/ArchitectureRulesTest.java", 18, "Guarantees domain core has zero incoming dependencies from web controllers or JPA repositories"),
                new CodeReference("ArchUnit JUnit 5 Dependency Configuration", "services/control-center/pom.xml", 39, "Integrates ArchUnit test runner into standard Maven test phase for zero-cost automated enforcement")
            ),
            "Pros: Prevents architectural erosion automatically, eliminates manual code review debates, enforces clean architecture objectively. Cons: Adds small bytecode scanning overhead during test execution phase.",
            "overview"
        ),
        new LearningPattern(
            "pattern-terraform-localstack-iac",
            "Cloud Infrastructure-as-Code (Terraform & LocalStack)",
            "Cloud & Platform Engineering",
            "Manual cloud resource creation (S3 buckets, SQS queues, DynamoDB tables, SNS topics) in web consoles leads to configuration drift, environment mismatch, zero reproducibility, and security misconfigurations.",
            "Declarative Infrastructure-as-Code (IaC) using HashiCorp Terraform modules. Cloud resources are codified in version-controlled HCL, executed locally against LocalStack with custom AWS endpoint overrides for zero-cost offline validation.",
            "Modular Terraform architecture under terraform/ with dedicated modules for messaging (SNS order-events-topic, SQS order-events-queue, inventory-events-queue, order-events-dlq) and storage (S3 order-invoices-bucket, DynamoDB inventory-items table) wired to LocalStack localhost:4566.",
            List.of("notification-service", "inventory-service", "control-center"),
            "terraform/main.tf:1",
            List.of(
                new CodeReference("Terraform Root Module & LocalStack Wiring", "terraform/main.tf", 1, "Orchestrates messaging and storage modules with LocalStack environment variables"),
                new CodeReference("Terraform AWS Provider & Endpoint Overrides", "terraform/providers.tf", 1, "Configures AWS provider with s3, sqs, sns, dynamodb endpoints mapped to http://localhost:4566"),
                new CodeReference("Messaging IaC Module (SQS, SNS, DLQ)", "terraform/modules/messaging/main.tf", 1, "Codifies SNS topic, SQS queues, subscriptions with raw message delivery, and SQS DLQ redrive policies"),
                new CodeReference("Storage IaC Module (S3 & DynamoDB)", "terraform/modules/storage/main.tf", 1, "Codifies S3 invoice archive bucket with versioning and DynamoDB inventory table with PAY_PER_REQUEST billing")
            ),
            "Pros: 100% reproducible cloud infrastructure, zero-drift declarative configuration, 100% local testing with LocalStack before deploying to production AWS. Cons: Requires managing Terraform state and module variable abstractions.",
            "overview"
        ),
        new LearningPattern(
            "pattern-umbrella-helm",
            "Cloud-Native Kubernetes Packaging (Umbrella Helm Chart)",
            "Cloud & Platform Engineering",
            "Deploying 7 microservices and 9 infrastructure containers into Kubernetes using separate raw YAML manifests creates operational chaos, configuration duplication, and painful environment promotion (dev vs staging vs prod).",
            "Umbrella Helm Chart pattern packaging the entire microservice ecosystem as a single deployable release. Parameterizes CPU/memory resources, replicas, environment variables, ingress hostnames, and configmaps per target environment.",
            "An umbrella Helm chart in helm/ecommerce-platform with unified deployments, services, ingress, and configmaps, complemented by environment-specific values overlays (values.yaml, values-dev.yaml, values-prod.yaml) and standalone sub-charts in each service.",
            List.of("api-gateway", "order-service", "inventory-service", "product-service", "payment-service", "notification-service", "control-center"),
            "helm/ecommerce-platform/Chart.yaml:1",
            List.of(
                new CodeReference("Umbrella Helm Chart Metadata Definition", "helm/ecommerce-platform/Chart.yaml", 1, "Defines chart version, application version, description, and microservice ecosystem dependencies"),
                new CodeReference("Base Platform Helm Values & Service Config", "helm/ecommerce-platform/values.yaml", 1, "Default resource requests/limits, container ports, image tags, and replica counts for all 7 services"),
                new CodeReference("Production Helm Environment Values Overlay", "helm/ecommerce-platform/values-prod.yaml", 1, "High-availability production overlay configuring horizontal pod autoscaling, higher CPU/RAM, and ingress TLS"),
                new CodeReference("Kubernetes Unified Microservices Deployment Template", "helm/ecommerce-platform/templates/deployments.yaml", 1, "Parameterized Kubernetes Deployment template orchestrating containers, probes, and env vars")
            ),
            "Pros: Single-command deployment ('helm upgrade --install'), consistent multi-environment configuration, automated rolling updates and rollbacks. Cons: Templating complexity in values inheritance.",
            "overview"
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
