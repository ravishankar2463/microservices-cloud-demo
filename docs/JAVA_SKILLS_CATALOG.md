# Java Skills & Architecture Concepts Catalog

This reference catalog details every Java skill, architectural style, and engineering pattern demonstrated throughout this multi-service repository.

---

## 1. Modern Java (Java 21+ LTS) Core Features

### A. Records (Immutable Data Carriers)
- **Concept**: Eliminates boilerplate getters, constructors, `equals()`, `hashCode()`, and `toString()`, providing compiler-enforced shallow immutability.
- **Where Used**:
  - `com.ravi.common.domain.model.ApiResponse`: Standard immutable API response envelope.
  - `com.ravi.common.domain.event.OrderCreatedEvent`: Domain events serialized across AWS SQS.
  - DTOs and database projection records across all microservices.

### B. Sealed Classes & Interfaces (Algebraic Data Types)
- **Concept**: Restricts which classes or records may implement or extend an interface (`sealed ... permits ...`).
- **Where Used**:
  - `com.ravi.common.domain.event.OrderEvent`: Sealed domain event contract permitting only `OrderCreatedEvent`, `OrderPaidEvent`, and `OrderCancelledEvent`.
  - `com.ravi.common.domain.model.Result`: Functional monad sealed to `Result.Success` and `Result.Failure`.
- **Benefit**: Provides complete exhaustiveness guarantees during compilation.

### C. Pattern Matching for `switch` and `instanceof`
- **Concept**: Eliminates unsafe downcasting and nested `if-else` chains.
- **Where Used**:
  - Handling different event types in `OrderEvent.eventType()`.
  - Event dispatcher in `notification-service` decomposing different message payloads without manual casting.

```java
public String handleEvent(OrderEvent event) {
    return switch (event) {
        case OrderCreatedEvent oce -> "Order created for customer: " + oce.customerId();
        case OrderPaidEvent ope -> "Payment captured: " + ope.paymentReference();
        case OrderCancelledEvent oce -> "Cancellation reason: " + oce.reason();
    };
}
```

### D. Virtual Threads (Project Loom)
- **Concept**: Lightweight user-mode threads managed by the JVM rather than the OS kernel. Enables synchronous, blocking code to achieve the high-throughput characteristics of reactive programming without callback hell.
- **Where Used**:
  - Enabled globally in `application.yaml` via `spring.threads.virtual.enabled: true`.
  - Used in `inventory-service` for handling concurrent I/O-bound requests against Redis and DynamoDB.
  - Used in `notification-service` for parallel document generation and S3 uploads.

### E. Defensive Immutability & Functional Design
- **Concept**: Never mutate existing data structures. Return new copies with state changes.
- **Where Used**:
  - `Result<T, E>` monad for error handling without unchecked exceptions.
  - Use of `List.copyOf()`, `Map.of()`, and `Stream` transformations across all services.

---

## 2. Software Architecture & Design Patterns

| Pattern | Service | Implementation Details |
| :--- | :--- | :--- |
| **Hexagonal Architecture** | `order-service` | Strict domain isolation. Inbound ports (`PlaceOrderUseCase`), outbound ports (`OrderRepositoryPort`, `OrderEventPublisherPort`), and external adapters. |
| **Transactional Outbox** | `order-service` | Guarantees atomic state updates and event publishing to AWS SQS without distributed 2-Phase Commit (2PC) transactions. |
| **Event-Driven Architecture (EDA)** | System-wide | AWS SNS/SQS event bus decouples producer services from consumer services. |
| **Cache-Aside Pattern** | `inventory-service` | Cache read through Redis; fall back to primary storage; cache write on update. |
| **Idempotent Consumer** | `notification-service` | Tracks event GUIDs in memory/storage to ensure messages processed multiple times from SQS produce identical outcomes without duplicate emails or charges. |
| **Dead Letter Queue (DLQ)** | `notification-service` | Poison-pill message isolation via AWS SQS redrive policy. |
| **Circuit Breaker & Fallback** | `inventory-service` | Resilience4j circuit breaker trips during high latency, returning cached fallback data. |

---

## 3. Cloud & AWS Integration (Zero Sign-in Local Emulation)

- **AWS SDK v2 (Java)**:
  - Non-blocking, modern Java AWS client architecture (`software.amazon.awssdk`).
  - Dynamic endpoint overrides configured in `common-aws` to route client traffic to the local emulator (`http://localhost:4566`).
- **AWS SQS (Simple Queue Service)**:
  - Asynchronous event bus for order processing.
- **AWS SNS (Simple Notification Service)**:
  - Pub/Sub topic with fan-out subscriptions to multiple microservice queues.
- **AWS S3 (Simple Storage Service)**:
  - Local bucket storage for generated PDF invoices and email archives using path-style access.
- **AWS DynamoDB**:
  - High-throughput NoSQL document storage for inventory state.

---

## 4. Testing & Quality Engineering

- **Test-Driven Development (TDD)**: Test suites written to verify domain logic and event contracts before feature deployment.
- **Slice & Unit Testing**: `@SpringBootTest`, Spring Boot context runners (`ApplicationContextRunner`), Mockito, and AssertJ.
- **Integration Testing with Testcontainers**: Automated spin-up of real containerized PostgreSQL, Redis, and LocalStack instances inside JUnit 5 tests.
