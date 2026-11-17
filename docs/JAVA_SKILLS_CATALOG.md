# Java Skills & Architecture Concepts Catalog

This reference catalog details every Java skill, architectural style, and engineering pattern demonstrated throughout this multi-service repository.

---

## 1. CQRS (Command Query Responsibility Segregation)

- **Command Side (Write Model)**:
  - In `order-service`, mutations are modeled as explicit immutable commands: [CreateOrderCommand.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/command/CreateOrderCommand.java) and [CancelOrderCommand.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/command/CancelOrderCommand.java).
  - Executed by [OrderCommandHandler.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/command/OrderCommandHandler.java) within a transactional boundary.
  - Persists domain state to relational database (`orders` table) and generates domain events stored in the `outbox_events` table in the same transaction.
- **Query Side (Read Model)**:
  - Query requests are handled by [OrderQueryHandler.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/query/OrderQueryHandler.java).
  - Materialized read views ([OrderView.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/query/OrderView.java)) are cached in **Redis** with TTL. Queries never lock the write tables.

---

## 2. Event-Driven Architecture (EDA) with Apache Kafka & AWS SQS

- **Transactional Outbox Pattern**:
  - Implemented in [OutboxPublisher.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/event/OutboxPublisher.java).
  - Solves the dual-write problem by guaranteeing atomic local DB commit and subsequent asynchronous broadcast to Kafka topic `order-events` and AWS SQS queue `order-events-queue`.
- **Event Streaming with Kafka**:
  - Apache Kafka in KRaft mode acts as the high-throughput distributed commit log.
  - Subscribed to by `inventory-service` for automatic stock reservation and `notification-service` for invoice generation.
- **Idempotent Consumer Pattern**:
  - Implemented in [IdempotentMessageStore.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/notification-service/src/main/java/com/ravi/notification/service/IdempotentMessageStore.java).
  - Protects consumers from at-least-once message delivery duplicates.

---

## 3. High-Performance Caching & Distributed Locks with Redis

- **Redis Cache-Aside Pattern**:
  - In `order-service`, order read projections check Redis cache key `order:view:{id}` before falling back to PostgreSQL.
- **Redis Distributed Locking**:
  - Implemented in [RedisDistributedLock.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/inventory-service/src/main/java/com/ravi/inventory/service/RedisDistributedLock.java).
  - Uses `SET key token NX PX timeout` to ensure that concurrent stock reservation requests for the same product across multiple service replicas execute atomically, preventing overselling.

---

## 4. Modern Java (Java 21+ LTS) Core Features

- **Virtual Threads (Project Loom)**:
  - Enabled via `spring.threads.virtual.enabled: true`.
  - In `inventory-service`, demonstrated in [InventoryReservationService.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/inventory-service/src/main/java/com/ravi/inventory/service/InventoryReservationService.java) using `Executors.newVirtualThreadPerTaskExecutor()`, handling parallel stock audits without thread-pool starvation.
- **Records as Immutable Data Carriers**:
  - Used for commands, queries, domain events, and read views ([OrderCreatedEvent.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/event/OrderCreatedEvent.java), [OrderView.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/query/OrderView.java)).
- **Sealed Interfaces & Pattern Matching**:
  - Implemented in [OrderEvent.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/order-service/src/main/java/com/ravi/order/event/OrderEvent.java) for exhaustive compiler-checked event type dispatching.

---

## 5. API Gateway & Security Ingress

- **Distributed Tracing Filter**:
  - Implemented in [CorrelationIdFilter.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/api-gateway/src/main/java/com/ravi/gateway/filter/CorrelationIdFilter.java) to inject `X-Correlation-ID` across HTTP headers and logging MDC contexts.
- **Token Bucket Rate Limiting**:
  - Implemented in [RateLimiterService.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/api-gateway/src/main/java/com/ravi/gateway/ratelimit/RateLimiterService.java) to mitigate denial-of-service traffic spikes.
- **Edge Reverse Proxy**:
  - Implemented in [GatewayRoutingController.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/api-gateway/src/main/java/com/ravi/gateway/controller/GatewayRoutingController.java) forwarding client traffic to downstream microservice ports.
