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
