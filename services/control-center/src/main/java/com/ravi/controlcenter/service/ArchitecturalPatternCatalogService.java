package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.ArchitecturalDossier;
import com.ravi.controlcenter.model.ArchitecturalDossier.HyperscaleBlueprint;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Educational masterclass dossier catalog for distributed transaction patterns.
 * Provides deep architectural insights, pros/cons, and hyperscale evolution blueprints.
 */
@Service
public class ArchitecturalPatternCatalogService {

    private final Map<String, ArchitecturalDossier> catalog = new ConcurrentHashMap<>();

    public ArchitecturalPatternCatalogService() {
        initCatalog();
    }

    public ArchitecturalDossier getDossierForStep(String stepName) {
        if (stepName == null) {
            return getDefaultDossier("UNKNOWN_STEP");
        }
        return catalog.getOrDefault(stepName.toUpperCase(), getDefaultDossier(stepName));
    }

    private void initCatalog() {
        catalog.put("ORDER_CREATED", new ArchitecturalDossier(
            "Transactional Outbox Pattern",
            "Data Consistency & Event Reliability",
            "Persisted Order entity into 'orders' table and simultaneously wrote an event record to 'outbox_events' within a single atomic ACID transaction.",
            "Guarantees that event messages are never lost if the Kafka broker is temporarily unreachable or the service crashes immediately after DB commit.",
            "Solves the Dual-Write Hazard. Direct writes to both PostgreSQL and Kafka in the same HTTP thread cannot be coordinated atomically without slow 2PC. Storing the event in the local DB guarantees at-least-once delivery with zero phantom orders.",
            List.of(
                "Eliminates slow, fragile two-phase commit (2PC / XA) distributed transactions",
                "Guaranteed at-least-once message delivery even during broker outages",
                "Local database ACID boundaries guarantee perfect consistency for initial state",
                "Allows asynchronous decoupled scaling of database writes and Kafka stream publishing"
            ),
            List.of(
                "Downstream consumers must implement idempotent message deduplication",
                "Introduces brief polling/CDC latency between DB commit and Kafka topic arrival",
                "Adds extra write I/O and table storage overhead to PostgreSQL outbox table"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Scheduled background polling thread (@Scheduled) selecting unpublished rows from PostgreSQL outbox table",
                "Database polling queries cause high read I/O contention, table locking, and replica lag at >10,000 RPS",
                "50,000 – 100,000+ RPS",
                "Log-based Change Data Capture (CDC) with Debezium / Kafka Connect reading PostgreSQL write-ahead log (WAL) directly with zero polling query overhead",
                "Deploy Debezium PostgreSQL connector reading WAL replication slot; deprecate polling scheduler; switch outbox table to unlogged or partition by day"
            )
        ));

        catalog.put("INVENTORY_RESERVED", new ArchitecturalDossier(
            "Distributed Lock with TTL & Cache-Aside",
            "Concurrency Control & State Management",
            "Acquired an atomic distributed mutex in Redis ('SET lock:stock:PROD-001 <token> NX PX 3000'), decremented in-memory stock count, and released lock.",
            "Prevents race conditions, double-allocation, and inventory overselling when hundreds of concurrent orders target the same SKU.",
            "Replaces slow, blocking database row locks ('SELECT ... FOR UPDATE') with microsecond-latency Redis distributed lock leases, protecting PostgreSQL connection pools from thread exhaustion.",
            List.of(
                "Sub-millisecond latency for high-concurrency stock reservations",
                "Automatic TTL lease expiration prevents permanent deadlocks if service pod crashes",
                "Atomic single-command Redis execution prevents split-brain reservations",
                "Isolates heavy checkout read/write spikes away from primary relational database"
            ),
            List.of(
                "System clock drift between cluster nodes can cause premature lock expiration",
                "Standalone Redis node is a single point of failure without replica failover",
                "Node reboot without fsync=always could drop recent in-flight reservation deltas"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Standalone Redis key with atomic 'SET NX PX' and local lock token",
                "Single Redis master node saturates on CPU/memory bandwidth; network partition causes lock safety violations",
                "50,000 – 100,000+ RPS",
                "Multi-node Redlock consensus protocol across 5 independent Redis clusters with monotonically increasing Fencing Tokens verified at the DB layer, or pre-allocated inventory bucket sharding",
                "Migrate to Redis Cluster with inventory sharded across hash slots by SKU ID ('{SKU_ID}:stock'); add fencing tokens to order DB updates"
            )
        ));

        catalog.put("INVENTORY_REJECTED", new ArchitecturalDossier(
            "Stock Boundary Guard & Event-Driven Saga Abort",
            "Concurrency Control & Defensive Invariants",
            "Acquired Redis distributed lock, verified requested quantity exceeded available stock, rejected the reservation with zero inventory mutation, and published InventoryRejectedEvent to Kafka topic 'inventory-events'.",
            "Guarantees zero overselling under load while immediately signaling OrderSagaOrchestrator to cancel the pending order without charging payment or touching other orders' reserved stock.",
            "Enforces domain invariants inside the owning microservice boundary. Because inventory-service never allows negative stock and broadcasts an explicit rejection event, the distributed saga terminates deterministically instead of hanging in PENDING/IN_PROGRESS.",
            List.of(
                "Strict zero-overselling invariant enforced inside the distributed lock",
                "Zero unintended mutation of concurrent orders' reserved stock",
                "Explicit InventoryRejectedEvent triggers deterministic Saga cancellation in order-service",
                "Prevents unnecessary downstream payment authorization calls for unfulfillable orders"
            ),
            List.of(
                "Asynchronous event choreography introduces brief eventual consistency window before order transitions to CANCELLED",
                "Requires per-order reservation tracking so subsequent OrderCancelledEvent does not release unrelated reservations"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Distributed Redis mutex check followed by Kafka InventoryRejectedEvent broadcast",
                "High-volume flash-sale rejection bursts saturate order_db writes and Kafka partitions",
                "50,000 – 100,000+ RPS",
                "Pre-flight Lua script stock counter in Redis Cluster with edge API Gateway stock-depleted circuit breakers and partitioned inventory-events topics",
                "Execute atomic Redis Lua DECRBY-if-sufficient script at ingress and publish only committed/rejected state changes asynchronously"
            )
        ));

        catalog.put("INVENTORY_COMMITTED", new ArchitecturalDossier(
            "Two-Phase Inventory Hold & Finalization (Reserve -> Commit)",
            "Concurrency Control & Fulfillment Integrity",
            "Consumed ORDER_CONFIRMED, acquired the Redis distributed mutex, cleared the soft reservation hold (RESERVED -1 while keeping AVAIL permanently sold), and emitted InventoryCommittedEvent to Kafka topic 'inventory-events'.",
            "Ensures reserved stock counts only represent in-flight unpaid orders and finalizes sold inventory for warehouse dispatch without lingering holds.",
            "Completes the Try-Confirm/Cancel (TCC) inventory pattern. Pending orders hold stock temporarily in RESERVED; cancelled orders return it to AVAIL, while confirmed orders commit and clear the hold permanently.",
            List.of(
                "Clean separation between in-flight soft holds (RESERVED) and permanently sold stock",
                "Prevents confirmed orders from ever being mistakenly released as unpaid cancellations",
                "Idempotent per-order reservation tracking handles duplicate Kafka deliveries safely",
                "Signals OrderSagaOrchestrator to finalize the order lifecycle to COMPLETED"
            ),
            List.of(
                "Requires durable reservation ledger in production so holds survive pod restarts before commit"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Redis distributed lock with per-order allocation map and Kafka commit event",
                "In-memory allocation tracking resets on pod crash if not backed by Redis/DynamoDB",
                "50,000 – 100,000+ RPS",
                "DynamoDB conditional TCC ledger with TTL-based reservation expiry reaper and DynamoDB Streams -> Kafka commit fan-out",
                "Persist per-order holds in DynamoDB with TTL; commit atomically via TransactWriteItems on ORDER_CONFIRMED"
            )
        ));

        catalog.put("ORDER_COMPLETED", new ArchitecturalDossier(
            "Saga Fulfillment Terminal State Transition",
            "Saga Lifecycle & State Machine",
            "OrderSagaOrchestrator consumed INVENTORY_COMMITTED, transitioned Order status in PostgreSQL order_db from CONFIRMED to COMPLETED, evicted stale CQRS cache, and published ORDER_COMPLETED via Transactional Outbox.",
            "Closes the distributed checkout and fulfillment saga once payment is captured, invoice is archived, and reserved inventory is permanently committed for dispatch.",
            "Provides a true end-to-end finite state machine (PENDING -> CONFIRMED -> COMPLETED) backed by real cross-service events rather than stopping mid-flight with dangling stock holds.",
            List.of(
                "Deterministic terminal fulfillment state persisted in order_db",
                "Zero lingering inventory holds once order reaches COMPLETED",
                "Outbox event notifies downstream analytics, shipping, and customer portals reliably"
            ),
            List.of(
                "Additional outbox hop adds one polling interval (~1s) before terminal completion event appears"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Transactional Outbox polling publisher updating PostgreSQL order_db",
                "Multi-step fulfillment workflows (pack, ship, deliver) outgrow flat status columns",
                "50,000 – 100,000+ RPS",
                "Dedicated Fulfillment / Shipping bounded context orchestrated via Temporal or Debezium CDC event streams",
                "Split post-payment warehouse dispatch, carrier tracking, and delivery confirmation into a dedicated fulfillment-service"
            )
        ));

        catalog.put("PAYMENT_CAPTURED", new ArchitecturalDossier(
            "Idempotency Key & Circuit-Resilient External Gateway",
            "Financial Integrity & Distributed Safety",
            "Generated an idempotency key, checked for prior duplicate submissions, captured payment authorization, and persisted the audit ledger entry.",
            "Protects customers and payment processors from double-charging during network retries, connection drops, or upstream timeouts.",
            "Enforces exactly-once business semantics over at-least-once transport. If a network packet drops after charging the credit card, upstream saga coordinators can safely retry with the exact same idempotency key without initiating a duplicate card charge.",
            List.of(
                "Absolute protection against duplicate monetary transactions",
                "Resilient to network timeouts and aggressive client retry policies",
                "Provides an immutable, tamper-evident audit ledger for financial compliance and reconciliation",
                "Decouples gateway latency spikes from synchronous user-facing API threads"
            ),
            List.of(
                "Idempotency key storage requires TTL eviction and garbage collection",
                "Payment provider asynchronous webhook delays can cause transient out-of-order state transitions"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "PostgreSQL unique constraint on 'idempotency_key' table with in-memory transaction token",
                "High write concurrency on idempotency table creates b-tree index fragmentation and connection pool exhaustion",
                "50,000 – 100,000+ RPS",
                "Two-phase idempotency filter in distributed Redis/DynamoDB with token-bucket rate limiting, asynchronous settlement queues, and distributed token leases",
                "Route incoming payment requests through Redis bloom filter / cache tier with TTL before database insertion; offload settlement to Kafka asynchronous event pipeline"
            )
        ));

        catalog.put("PAYMENT_DECLINED", new ArchitecturalDossier(
            "Controlled Failure Trigger & Saga Abort",
            "Failure Recovery & Eventual Consistency",
            "Recorded a simulated or real payment rejection, marked payment step as FAILED, and published PaymentFailedEvent to Kafka to trigger compensation.",
            "Prevents unpaid orders from progressing to fulfillment while cleanly signaling upstream saga steps to unwind.",
            "Demonstrates the fundamental principle of Saga coordination: failure is not an unhandled exception or crash, but a first-class business state that triggers choreographed compensation across other services.",
            List.of(
                "Failures are handled deterministically without leaving dangling database locks",
                "Triggers automated compensating transactions across inventory and order services",
                "Provides clear audit trail explaining why the checkout flow was halted"
            ),
            List.of(
                "Requires all upstream participants to have implemented reversible compensation actions",
                "System is temporarily in an inconsistent state until all compensation events complete"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Synchronous payment call rejection broadcasting Kafka event",
                "Kafka event storm if hundreds of card payments fail simultaneously during flash sale",
                "50,000 – 100,000+ RPS",
                "Dead-letter queue (DLQ) with exponential backoff retry and circuit-breaking isolation",
                "Introduce Kafka DLQ topic for unrecoverable payment failures; rate-limit compensation events"
            )
        ));

        catalog.put("COMPENSATING_INVENTORY", new ArchitecturalDossier(
            "Compensating Transaction Pattern (Saga Rollback)",
            "Failure Recovery & Eventual Consistency",
            "Reverted previously reserved inventory items by publishing InventoryRestoredEvent and incrementing Redis and database stock allocations.",
            "Re-establishes global system consistency after downstream payment authorization failed, without using blocking distributed two-phase commit (2PC).",
            "Eliminates the fatal coordination bottlenecks of distributed ACID transactions (XA/2PC). Instead of locking all databases across microservices for the entire transaction lifecycle, each microservice commits locally immediately and registers compensating transactions to unwind changes if downstream steps fail.",
            List.of(
                "Zero cross-service database locks; high throughput and service autonomy",
                "Microservices remain decoupled and independently deployable",
                "Resilient to transient outages of individual downstream services",
                "Enables high-availability architectures where services can fail independently"
            ),
            List.of(
                "Intermediate inconsistent states are visible to other read queries (isolation anomaly)",
                "Compensating logic must be explicitly written, tested, and maintained for every business step",
                "Requires semantic rollback rather than simple raw DB rollback (e.g. stock restored, not unwritten)"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Choreographed saga coordination via Kafka topic events",
                "Event choreography becomes an unmaintainable 'spiderweb' of circular event dependencies; tracing rollback state across dozens of services is difficult",
                "50,000 – 100,000+ RPS",
                "Hybrid Orchestration Engine (e.g., Temporal / AWS Step Functions / Camunda) with persistent deterministic state machines and distributed event streaming",
                "Keep choreographed events for simple pairwise interactions; migrate long-running multi-stage checkout flows to a Temporal workflow worker cluster"
            )
        ));

        catalog.put("ORDER_CANCELLED", new ArchitecturalDossier(
            "Saga Terminal Rollback State Transition",
            "Saga Lifecycle & State Machine",
            "Transitioned Order entity status in PostgreSQL to CANCELLED and published OrderCancelledEvent to Kafka.",
            "Closes the active saga lifecycle, releasing all reservations and notifying customer and analytics systems.",
            "Implements a deterministic finite state machine (FSM). Orders cannot jump into arbitrary states; transitions are guarded by preconditions, preventing invalid state regressions.",
            List.of(
                "Deterministic, auditable lifecycle states",
                "Guaranteed event broadcast for downstream reactive consumers",
                "Idempotent state transition guards prevent duplicate cancellations"
            ),
            List.of(
                "Requires distributed tracing (TraceId/SpanId) to correlate terminal events back to initiating HTTP requests"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct JPA repository update in order-service",
                "Write locks on order rows if multiple event consumers attempt concurrent updates",
                "50,000 – 100,000+ RPS",
                "Event Sourcing (CQRS) with append-only event stream (Kafka or EventStoreDB) and materialized views",
                "Switch from updating mutable order rows to appending OrderStateChanged events to an order event stream"
            )
        ));

        catalog.put("ORDER_CONFIRMED", new ArchitecturalDossier(
            "Saga Terminal Success State Transition",
            "Saga Lifecycle & State Machine",
            "Transitioned Order entity status in PostgreSQL to CONFIRMED and broadcasted OrderConfirmedEvent to Kafka.",
            "Marks the end-to-end saga transaction as successfully fulfilled, authorizing shipping and invoice generation.",
            "Fulfills the saga contract. All local transactions (Order, Inventory, Payment) succeeded independently and are now committed permanently.",
            List.of(
                "Permanent business confirmation without long-lived locks",
                "Asynchronously triggers downstream invoice generation, email dispatch, and logistics packaging"
            ),
            List.of(
                "Requires reliable at-least-once downstream message handling"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct JPA repository update in order-service",
                "Database connection contention under massive concurrent order commits",
                "50,000 – 100,000+ RPS",
                "Partitioned PostgreSQL sharding by CustomerId with Kafka partition key ordering",
                "Shard orders database across customer tenant IDs; route order events with customerId partition key"
            )
        ));

        catalog.put("ORDER_REJECTED", new ArchitecturalDossier(
            "Fast-Fail Terminal State Transition",
            "Saga Lifecycle & State Machine",
            "Marked Order entity as REJECTED due to stock pre-check failure without initiating payment.",
            "Immediately halts the transaction pipeline when preconditions are unsatisfied, saving downstream compute.",
            "Defensive microservice boundary pattern. Validates business constraints before committing expensive transactional resources.",
            List.of(
                "Zero downstream impact on payment or notification services",
                "Immediate feedback to caller (<15ms)"
            ),
            List.of(
                "Requires client-side handling of immediate rejection"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct REST rejection response",
                "Connection pool saturation if inventory is heavily contended",
                "50,000 – 100,000+ RPS",
                "Edge API Gateway rate-limiting and inventory cache pre-checks at CDN layer",
                "Push inventory availability flags to API Gateway / CDN edge to reject depleted items before hitting backend"
            )
        ));

        catalog.put("ORDER_FAILED", new ArchitecturalDossier(
            "Saga Failure Terminal State Transition",
            "Saga Lifecycle & State Machine",
            "Marked Order entity as FAILED following downstream timeout and compensation execution.",
            "Finalizes saga records when an external dependency times out, ensuring no zombie orders exist.",
            "Maintains data integrity when external integrations breach SLA boundaries.",
            List.of(
                "System remains clean and self-healing",
                "All temporary allocations are verified as released"
            ),
            List.of(
                "Requires post-mortem logging and metric alerting for SLA monitoring"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Synchronous timeout handling in controller",
                "Worker thread starvation while waiting for timeouts to expire",
                "50,000 – 100,000+ RPS",
                "Virtual threads with asynchronous non-blocking timeout futures and distributed circuit breakers",
                "Leverage Java 21 Virtual Threads and Resilience4j time limiters to eliminate OS thread blocking"
            )
        ));

        catalog.put("INVOICE_GENERATED", new ArchitecturalDossier(
            "Asynchronous Event-Driven Cloud Storage",
            "Cloud Integration & Decoupled Storage",
            "Consumed OrderConfirmedEvent from Kafka, rendered invoice payload, and stored document in AWS S3 ('order-invoices-bucket').",
            "Offloads document generation and object storage outside the synchronous user checkout request thread.",
            "Pure reactive event consumer. If AWS S3 or notification service slows down, user checkout latency is completely unaffected.",
            List.of(
                "Zero impact on core user checkout response latency",
                "AWS S3 provides 99.999999999% (11 9's) durability for legal/financial invoices",
                "Independent auto-scaling of document generation workers"
            ),
            List.of(
                "Asynchronous generation means invoice is not available in immediate HTTP response",
                "Requires client polling or WebSocket push for real-time document delivery"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Notification service generates document in memory and uploads directly to single S3 bucket root",
                "S3 request throttling (3,500 PUT requests/sec per prefix) and memory exhaustion from high-volume PDF rendering",
                "50,000 – 100,000+ RPS",
                "S3 prefix partitioning with hash-prefixing ('s3://bucket/invoices/{hash(orderId)[0:2]}/{yyyy}/{mm}/{dd}/{orderId}.pdf') and serverless AWS Lambda worker fleet auto-scaling independently",
                "Add S3 key hash partitioning utility; offload document rasterization to dedicated AWS Lambda/Fargate worker pool"
            )
        ));

        catalog.put("INVENTORY_CHECK", new ArchitecturalDossier(
            "Fast-Fail Pre-Flight Validation",
            "Resilience & Defensive Design",
            "Queried inventory cache for available stock before locking or initiating downstream payments.",
            "Avoids initiating payment processing or writing outbox events when stock is obviously depleted.",
            "Fast-fail validation optimizes system throughput by rejecting impossible requests at the edge before incurring expensive transactional database and payment processor costs.",
            List.of(
                "Conserves downstream computational and financial resources",
                "Instantaneous failure response to client (<10ms)"
            ),
            List.of(
                "Read-after-write consistency lag if cache is slightly behind database"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct Redis GET query",
                "Hot-key contention on viral flash-sale items during high-traffic events",
                "50,000 – 100,000+ RPS",
                "Local in-process L1 cache (Caffeine) with Redis pub/sub invalidation bus or probabilistic Bloom filters",
                "Add multi-tier caching (L1 Caffeine in-memory cache + L2 Redis cluster with pub/sub invalidation)"
            )
        ));

        catalog.put("PAYMENT_TIMEOUT", new ArchitecturalDossier(
            "Circuit Breaker & Timeout Compensation",
            "Fault Tolerance & Fault Isolation",
            "Detected payment gateway timeout, short-circuited the wait loop, marked step failed, and triggered saga compensation.",
            "Prevents thread starvation and cascading failures across microservices when external dependencies degrade.",
            "Implements the Fail-Fast and Bulkhead principles. Without explicit timeout boundaries, hanging payment socket connections would exhaust Tomcat/Netty worker threads and bring down the entire order cluster.",
            List.of(
                "Protects system stability and preserves worker thread pools",
                "Initiates deterministic compensating rollback"
            ),
            List.of(
                "Can trigger compensation for a transaction that actually succeeded on payment gateway side (requires reconciliation daemon)"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "HTTP client socket timeout and Resilience4j circuit breaker",
                "Synchronous thread-per-request blocking while waiting for timeout",
                "50,000 – 100,000+ RPS",
                "Asynchronous non-blocking reactive HTTP (Java 21 Virtual Threads / WebClient) with distributed bulkhead rate limiters and automatic async reconciliation queues",
                "Adopt Virtual Threads everywhere; route payment authorizations through asynchronous webhook callbacks rather than synchronous HTTP blocking"
            )
        ));

        catalog.put("DISTRIBUTED_TRACING", new ArchitecturalDossier(
            "Distributed Observability & Tracing (OpenTelemetry & Tempo)",
            "Distributed Observability",
            "Instrumented all microservices with OpenTelemetry, Micrometer Tracing, and W3C TraceContext propagation, streaming OTLP traces to Grafana Tempo and metrics to Prometheus.",
            "Eliminates distributed visibility blind spots, providing microsecond trace waterfalls that correlate HTTP calls and Kafka events across services.",
            "Solves distributed system diagnosability. Without distributed tracing, pin-pointing the root cause of an intermittent 2-second delay across 5 microservices requires manually grepping disparate log files.",
            List.of(
                "Unified trace-to-metrics correlation in Grafana dashboards",
                "W3C TraceContext headers propagate transparently across HTTP and Kafka topics",
                "Automated span capture for controller endpoints, RestClient dispatches, and Kafka listeners",
                "Zero application code changes required for standard span propagation"
            ),
            List.of(
                "Exporting 100% of spans adds minor network bandwidth and CPU overhead under extreme load",
                "Requires storage retention policies and compaction for trace storage backends"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct OTLP HTTP/gRPC export to single-instance Grafana Tempo with 100% sampling",
                "High-volume 100% trace sampling saturates Tempo ingestion queues and storage disks",
                "50,000 – 100,000+ RPS",
                "OpenTelemetry Collector daemonset with tail-based sampling (retaining 100% of errors and 1% of happy paths) with S3/GCS object storage backend",
                "Deploy OpenTelemetry Collector cluster; configure tail-based sampling policies to discard boring healthy 200 OK spans while preserving all latency anomalies and errors"
            )
        ));

        catalog.put("CIRCUIT_BREAKER", new ArchitecturalDossier(
            "Microservice Egress Resilience & Circuit Breaker (Resilience4j)",
            "Resilience Engineering",
            "Decorated microservice outbound integration boundaries (order-service ProductCatalogClient REST lookups and payment-service ExternalPaymentGateway authorizations) with @CircuitBreaker and @Retry.",
            "Protects calling microservices from thread starvation, socket timeout hangs, and cascading failures when external third-party APIs or internal dependency microservices degrade.",
            "Implements the Fail-Fast and Fault Isolation principles at the exact network seam. Rather than letting edge proxies guess at service health, each microservice protects its own egress calls, tripping OPEN to fail fast or execute domain-specific fallbacks (such as triggering Saga compensation).",
            List.of(
                "Protects microservice worker threads from starvation during downstream or external dependency outages",
                "Automatic sliding window monitoring (COUNT_BASED / TIME_BASED) with configurable failure thresholds",
                "Self-healing automatic transition from OPEN to HALF_OPEN to CLOSED once dependencies recover",
                "Enables clean domain-specific fallbacks (e.g. failing payment authorization to trigger deterministic Saga rollback)"
            ),
            List.of(
                "Fallbacks must be carefully designed to prevent masking fatal business errors",
                "Sliding window size and failure thresholds require tuning per service SLA"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "In-process Resilience4j circuit breaker state per microservice pod instance",
                "State is fragmented across 50 microservice replicas; one replica might remain closed while others are open",
                "50,000 – 100,000+ RPS",
                "Service Mesh (Envoy / Istio) egress circuit breaking and outlier detection at the L7 transport layer with adaptive concurrency limits",
                "Offload circuit breaking and retries to Envoy sidecars with outlier detection (ejection of unhealthy upstream hosts)"
            )
        ));

        catalog.put("DEAD_LETTER_QUEUE", new ArchitecturalDossier(
            "Dead Letter Queue (DLQ) Parking & Redrive Pattern",
            "Reliability & Eventing",
            "Isolated poison-pill messages from Kafka listener threads into DeadLetterQueueService with failure metadata, exposing an operator redrive and purge REST API.",
            "Prevents corrupted or unparseable messages from halting Kafka consumer partitions and blocking valid transactions from processing.",
            "Separates transient network errors (which should be retried with exponential backoff) from deterministic poison pills (which must be quarantined to a DLQ so the offset can commit).",
            List.of(
                "Guarantees consumer stream continuity without infinite crash-looping",
                "Full forensic capture of failure reason, exception message, and original payload",
                "Interactive operator API for selective message redrive after bug fix or database recovery",
                "Auditable purge capabilities for permanently invalid messages"
            ),
            List.of(
                "DLQ storage requires capacity limits and periodic cleanup of un-redrivable messages",
                "Out-of-order processing when a redriven message is replayed after later messages"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "In-memory / local DLQ store with REST API redrive endpoints",
                "Pod termination risks losing unpersisted parked DLQ messages if memory-only",
                "50,000 – 100,000+ RPS",
                "Durable Kafka dead-letter topics (e.g., 'notification-events-dlq') with automated backoff retry topics and AWS SQS DLQ redrive tasks",
                "Implement Spring Kafka DeadLetterPublishingRecoverer routing failed messages to dedicated .DLQ topics with exponential backoff retry topics (.RETRY-1, .RETRY-2)"
            )
        ));
        catalog.put("DLQ_PARKING", catalog.get("DEAD_LETTER_QUEUE"));

        catalog.put("PROBLEM_DETAIL", new ArchitecturalDossier(
            "RFC 7807 Standardized ProblemDetail API Errors",
            "API Design & Error Governance",
            "Implemented GlobalExceptionHandler extending ResponseEntityExceptionHandler across all services, producing uniform RFC 7807 ProblemDetail payloads.",
            "Eliminates discordant error payloads across services, providing predictable machine-readable error structures with standard status, title, detail, and instance fields.",
            "Establishes a single API error contract for all REST endpoints across the enterprise, preventing sensitive internal stack traces from leaking to external clients.",
            List.of(
                "IETF and Spring Boot standard format for HTTP API error representations",
                "Consistent client-side error parsing across web, mobile, and third-party integrations",
                "Encapsulates domain-specific metadata (timestamps, trace IDs, service origin) cleanly",
                "Secure by default: masks raw database exceptions and internal stack traces"
            ),
            List.of(
                "Requires comprehensive mapping of all custom business exceptions to standard HTTP statuses",
                "Client developers must be educated to parse standard RFC 7807 JSON keys"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Spring Boot @RestControllerAdvice GlobalExceptionHandler in each microservice",
                "Code duplication across microservice repositories if exception handlers diverge",
                "50,000 – 100,000+ RPS",
                "Shared corporate error-starter library or API Gateway error translation filter enforcing corporate error schema at the perimeter",
                "Package common error handling into shared corporate Spring Boot starter; configure API Gateway to normalize legacy upstream 5xx responses into RFC 7807 envelopes"
            )
        ));
        catalog.put("RFC7807_ERROR", catalog.get("PROBLEM_DETAIL"));

        catalog.put("ARCHUNIT_GUARDRAILS", new ArchitecturalDossier(
            "Architecture Fitness Functions (ArchUnit Guardrails)",
            "Architecture Governance",
            "Codified hexagonal architecture layer rules, package isolation invariants, and injection safety rules into automated ArchUnit unit tests.",
            "Prevents architectural erosion and unauthorized layer couplings from being merged into the codebase, verifying compliance during every 'mvn test' run.",
            "Treats architecture as executable code rather than static documentation. If a developer accidentally imports a web controller into a domain entity, the build fails immediately.",
            List.of(
                "Zero-cost automated enforcement in CI/CD pipeline",
                "Objective, non-negotiable verification of hexagonal layer boundaries",
                "Prevents cyclic dependencies and dangerous field injection (@Autowired on fields)",
                "Living documentation of architectural rules that never gets out of date"
            ),
            List.of(
                "Adds a few seconds of bytecode scanning time to the unit test execution phase",
                "Requires team alignment on package structure conventions"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Standard ArchUnit test classes inside src/test/java per microservice",
                "Rules duplicated across 50 repositories become difficult to update uniformly",
                "50,000 – 100,000+ RPS",
                "Shared ArchUnit rules artifact published to corporate Maven repo and enforced via Maven Enforcer plugin / SonarQube quality gates",
                "Extract common ArchUnit fitness functions into a shared Maven test dependency; enforce as mandatory corporate CI gate"
            )
        ));

        catalog.put("TERRAFORM_IAC", new ArchitecturalDossier(
            "Cloud Infrastructure-as-Code (Terraform & LocalStack)",
            "Cloud & Platform Engineering",
            "Codified AWS cloud resources (S3 buckets, SQS queues, SNS topics, DynamoDB tables) into modular HashiCorp Terraform modules verified against LocalStack.",
            "Eliminates manual cloud provisioning, configuration drift, and production deployment surprises by verifying 100% of cloud resources locally without AWS spend.",
            "Enforces immutable, declarative infrastructure versioning. The exact same Terraform modules that provision LocalStack resources locally can be promoted to staging and production AWS accounts.",
            List.of(
                "Declarative, reproducible cloud infrastructure definition",
                "Modular architecture separating messaging (SQS/SNS) from persistence (S3/DynamoDB)",
                "Zero-cost local validation with LocalStack before executing against real AWS",
                "Integrated state management and plan dry-run capabilities"
            ),
            List.of(
                "Terraform state file locking and remote state storage management required in teams",
                "LocalStack API parity nuances compared to advanced multi-region AWS features"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Local Terraform CLI execution against LocalStack endpoint overrides",
                "Local state files and manual CLI applies risk state corruption and concurrency conflicts",
                "50,000 – 100,000+ RPS",
                "GitOps IaC pipeline with Terraform Cloud / Spacelift / Atlantis and AWS remote S3 state backend with DynamoDB state locking",
                "Configure remote S3 state backend with DynamoDB locking; deploy Atlantis or Spacelift for automated plan/apply on pull requests"
            )
        ));

        catalog.put("HELM_PACKAGING", new ArchitecturalDossier(
            "Cloud-Native Kubernetes Packaging (Umbrella Helm Chart)",
            "Cloud & Platform Engineering",
            "Packaged all 7 microservices and configuration into an umbrella Helm chart with environment-specific values overlays (values-dev.yaml, values-prod.yaml).",
            "Eliminates sprawling unmaintainable Kubernetes YAML manifests, enabling single-command deployment, rolling upgrades, and environment promotion.",
            "Encapsulates Kubernetes best practices: parameterized replica counts, resource requests/limits, liveness/readiness probes, and ingress routing.",
            List.of(
                "Single-command ecosystem deployment ('helm upgrade --install')",
                "Environment-specific overrides for CPU, memory, replicas, and ingress",
                "Automated zero-downtime rolling upgrades and one-command rollbacks",
                "Standalone sub-chart support for team-level service autonomy"
            ),
            List.of(
                "Helm Go template syntax can become complex when nesting loops and conditionals",
                "Values inheritance requires clear documentation to avoid unexpected overrides"
            ),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Umbrella Helm chart deployed via local Helm CLI to Minikube / Kind",
                "Manual CLI deployments lack auditability, automated sync, and drift detection",
                "50,000 – 100,000+ RPS",
                "GitOps Continuous Delivery with ArgoCD or Flux managing Helm releases directly from Git commits with automated canary deployments (Argo Rollouts)",
                "Connect ArgoCD to git repository; declare Application CRDs targeting the umbrella Helm chart; enable automated drift detection and self-healing"
            )
        ));
    }

    private ArchitecturalDossier getDefaultDossier(String stepName) {
        return new ArchitecturalDossier(
            stepName,
            "Distributed Microservice Step",
            "Executed step " + stepName + " as part of the distributed saga transaction.",
            "Maintains service coordination and state synchronization across cluster nodes.",
            "Contributes to atomic progression or compensation of distributed state.",
            List.of("Service decoupling", "Auditability"),
            List.of("Network dependency overhead"),
            new HyperscaleBlueprint(
                "100 – 1,000 RPS",
                "Direct synchronous REST/Kafka coordination",
                "Network queue latency at high scale",
                "50,000 – 100,000+ RPS",
                "Partitioned Kafka event streams with dedicated partition keys",
                "Optimize topic partition count and consumer group concurrency"
            )
        );
    }
}
