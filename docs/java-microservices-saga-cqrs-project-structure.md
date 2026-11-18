# Enterprise-Grade Java Microservices Reference Architecture

## 1. Purpose

This document defines a recommended structure and engineering standard
for a production-oriented Java microservices platform using:

-   Domain-Driven Design (DDD)
-   Hexagonal Architecture (Ports and Adapters)
-   Clean Architecture principles
-   Command Query Responsibility Segregation (CQRS)
-   Saga pattern (prefer orchestration for complex workflows)
-   Event-Driven Architecture (EDA)
-   Transactional Outbox and Inbox patterns
-   Observability, resilience, security, automated testing, and CI/CD

The objective is to create services that are independently deployable,
maintainable, testable, observable, and understandable as the system
grows.

The architecture is a reference, not a requirement to implement every
pattern in every service. Apply patterns where they solve a real
problem.

------------------------------------------------------------------------

## 2. Architectural principles

1.  **Business domains own their logic and data.** Each microservice
    represents a bounded context and owns its persistence model.
2.  **Dependencies point inward.** Domain logic must not depend on
    Spring, JPA, Kafka, REST, or infrastructure implementations.
3.  **Use cases coordinate; domain models enforce business rules.**
4.  **CQRS is first a logical separation.** Separate read and write
    models physically only when there is a justified need.
5.  **Sagas coordinate distributed business transactions.** Do not
    attempt distributed ACID transactions across services.
6.  **Events are contracts.** Version, document, and evolve them
    deliberately.
7.  **Assume at-least-once message delivery.** Consumers must be
    idempotent.
8.  **Make failure handling explicit.** Include timeouts, retries,
    compensation, dead-letter handling, and recovery.
9.  **Observability is part of the design.** Logs, metrics, traces, and
    correlation identifiers must be available from the beginning.
10. **Prefer boring, well-understood solutions.** Avoid unnecessary
    frameworks, abstractions, shared libraries, and infrastructure.

## 3. High-level system architecture

``` text
Clients
   |
   v
API Gateway
   |-- Authentication / authorization
   |-- Routing
   |-- Rate limiting
   |-- Request correlation
   |
   +--------------------+---------------------+
   |                    |                     |
   v                    v                     v
Order Service      Payment Service      Inventory Service
   |                    |                     |
   +--------------------+---------------------+
                        |
                        v
              Messaging Infrastructure
              Kafka / RabbitMQ
              Event contracts / Schema Registry
                        |
          +-------------+-------------+
          |                           |
          v                           v
   Saga Orchestration            Event Consumers
   Saga state / retries          Inbox / idempotency
   Compensation                  Projections
          |                           |
          +-------------+-------------+
                        |
                        v
             Observability Platform
      OpenTelemetry / Prometheus / Grafana
          Logs / Traces / Alerts / Dashboards
```

Each service can internally use CQRS:

``` text
                 SERVICE BOUNDARY
                       |
          +------------+------------+
          |                         |
          v                         v
     COMMAND SIDE              QUERY SIDE
          |                         |
     Command Handler           Query Handler
          |                         |
     Domain Aggregate          Read Repository
          |                         |
     Write Model               Read Model
          |                         ^
          v                         |
     Local Database -- Events / Projections
```

CQRS and Saga are not mandatory sequential layers. CQRS structures use
cases within a service; Saga coordinates a business workflow across
services.

------------------------------------------------------------------------

## 4. Recommended microservice structure

Use a feature-oriented DDD structure with clear Hexagonal Architecture
boundaries. The following example is for an `order-service`.

``` text
order-service/
├── src/
│   ├── main/
│   │   ├── java/com/company/orders/
│   │   │   ├── OrderServiceApplication.java
│   │   │   │
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Order.java
│   │   │   │   │   ├── OrderItem.java
│   │   │   │   │   ├── OrderStatus.java
│   │   │   │   │   └── Money.java
│   │   │   │   ├── event/
│   │   │   │   │   ├── OrderCreatedEvent.java
│   │   │   │   │   └── OrderCancelledEvent.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── OrderRepository.java
│   │   │   │   ├── service/
│   │   │   │   └── exception/
│   │   │   │
│   │   │   ├── application/
│   │   │   │   ├── command/
│   │   │   │   │   ├── CreateOrderCommand.java
│   │   │   │   │   ├── CancelOrderCommand.java
│   │   │   │   │   └── handler/
│   │   │   │   ├── query/
│   │   │   │   │   ├── GetOrderByIdQuery.java
│   │   │   │   │   ├── SearchOrdersQuery.java
│   │   │   │   │   └── handler/
│   │   │   │   └── port/
│   │   │   │       ├── in/
│   │   │   │       └── out/
│   │   │   │
│   │   │   ├── saga/
│   │   │   │   ├── orchestration/
│   │   │   │   ├── state/
│   │   │   │   ├── step/
│   │   │   │   ├── compensation/
│   │   │   │   └── persistence/
│   │   │   │
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── mapper/
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── producer/
│   │   │   │   │   ├── consumer/
│   │   │   │   │   ├── outbox/
│   │   │   │   │   ├── inbox/
│   │   │   │   │   └── config/
│   │   │   │   ├── external/
│   │   │   │   ├── security/
│   │   │   │   └── config/
│   │   │   │
│   │   │   └── presentation/
│   │   │       ├── rest/
│   │   │       │   ├── controller/
│   │   │       │   ├── request/
│   │   │       │   └── response/
│   │   │       └── exception/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-test.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/
│   │
│   └── test/
│       └── java/com/company/orders/
│           ├── unit/
│           ├── integration/
│           ├── architecture/
│           ├── contract/
│           ├── component/
│           └── e2e/
│
├── Dockerfile
├── pom.xml
├── README.md
└── docs/
    ├── architecture.md
    └── api.md
```

### 4.1 Layer responsibilities

  -----------------------------------------------------------------------
  Layer                   Responsibility          Examples
  ----------------------- ----------------------- -----------------------
  `domain`                Business rules and      Aggregates, entities,
                          domain model            value objects, domain
                                                  events

  `application`           Use-case orchestration  Command handlers, query
                                                  handlers, ports

  `saga`                  Long-running            Saga state, steps,
                          distributed workflow    compensation

  `infrastructure`        Technical adapters      JPA, Kafka, Redis, HTTP
                                                  clients

  `presentation`          External entry points   REST controllers,
                                                  request/response DTOs
  -----------------------------------------------------------------------

### 4.2 Dependency rules

-   `domain` must not depend on `application`, `infrastructure`,
    `presentation`, Spring, JPA, or Kafka.
-   `application` may depend on `domain` and application-owned ports.
-   `infrastructure` implements ports defined inward (usually in
    `application`).
-   `presentation` invokes application use cases; it must not contain
    business logic.
-   Domain repository interfaces belong in the domain when they express
    aggregate persistence needs. Application ports may be used for other
    external capabilities.
-   Keep persistence entities and API DTOs separate from domain
    aggregates.
-   Avoid using one generic `common` package as a dumping ground.

The exact package placement of ports can vary. The important rule is
that abstractions are owned by the inner layer and implementations
remain in outer layers.

------------------------------------------------------------------------

## 5. CQRS implementation

CQRS separates operations that change state from operations that
retrieve data.

### 5.1 Suggested structure

``` text
application/
├── command/
│   ├── CreateOrderCommand.java
│   ├── CancelOrderCommand.java
│   └── handler/
│       ├── CreateOrderHandler.java
│       └── CancelOrderHandler.java
│
├── query/
│   ├── GetOrderByIdQuery.java
│   ├── SearchOrdersQuery.java
│   └── handler/
│       ├── GetOrderByIdHandler.java
│       └── SearchOrdersHandler.java
│
└── port/
    ├── in/
    └── out/
```

### 5.2 Command-side rules

-   Commands express business intent, e.g. `CreateOrder`, `CancelOrder`,
    or `ReserveInventory`.
-   Validate input at the boundary and enforce business invariants in
    the domain.
-   Command handlers coordinate one use case.
-   Commands should normally return an acknowledgement, result, or
    resource identifier rather than a large query model.
-   State changes should be persisted within a local transaction.
-   Publish integration events reliably, preferably through a
    transactional outbox.

### 5.3 Query-side rules

-   Queries must not change business state.
-   Query handlers should not call command handlers.
-   Use query-specific DTOs/read models rather than exposing JPA
    entities.
-   Optimise read paths for the query workload.
-   Support pagination, filtering, sorting, and appropriate access
    controls.
-   Document eventual consistency when projections update
    asynchronously.

### 5.4 Storage strategy

Start with logical CQRS separation and one database if that is
sufficient. Separate read storage is optional, not a CQRS requirement.

Consider a separate read store when there is a measurable need for
independent scaling, denormalised views, different query technology, or
workload isolation.

### 5.5 Projection reliability

-   Consume events idempotently.
-   Track projection progress or offsets.
-   Make projection handlers replayable where feasible.
-   Define how projections are rebuilt.
-   Monitor projection lag.
-   Handle out-of-order events using aggregate versions or sequence
    numbers where ordering matters.

------------------------------------------------------------------------

## 6. Saga pattern

Use Saga when a business transaction spans multiple independently owned
services and cannot be handled by one local database transaction.

### 6.1 Example: order placement

Participants:

-   Order Service
-   Inventory Service
-   Payment Service

Happy path:

``` text
Create Order (PENDING)
       |
       v
Reserve Inventory
       |
       v
Process Payment
       |
       v
Confirm Order
       |
       v
Order (CONFIRMED)
```

Failure path example:

``` text
Create Order
       |
       v
Reserve Inventory
       |
       v
Process Payment (successful)
       |
       v
A later step fails
       |
       v
Refund Payment (compensation)
       |
       v
Release Inventory (if reserved)
       |
       v
Cancel / mark order as failed
```

Compensation is a business action, not a database rollback. It may fail
and must itself be retried, monitored, and recoverable. Some actions are
not perfectly reversible; define business-specific recovery or manual
intervention for those cases.

### 6.2 Suggested Saga structure

``` text
saga/
├── orchestration/
│   ├── OrderSagaOrchestrator.java
│   ├── OrderSagaDefinition.java
│   └── OrderSagaCoordinator.java
├── state/
│   ├── OrderSagaState.java
│   ├── SagaStatus.java
│   └── SagaStepStatus.java
├── step/
│   ├── ReserveInventoryStep.java
│   ├── ProcessPaymentStep.java
│   └── ConfirmOrderStep.java
├── compensation/
│   ├── ReleaseInventoryCompensation.java
│   └── RefundPaymentCompensation.java
├── event/
└── persistence/
    └── SagaStateRepository.java
```

### 6.3 Saga engineering requirements

-   Persist workflow state and step outcomes.
-   Assign a unique Saga ID and correlation ID.
-   Make every command and consumer idempotent.
-   Define timeouts and retry policies per step.
-   Use exponential backoff with jitter for transient failures.
-   Distinguish retryable failures from permanent business failures.
-   Define compensation actions and their ordering.
-   Make compensation idempotent.
-   Support process restart and workflow recovery.
-   Record state transitions and audit information.
-   Provide operational visibility into stuck, failed, and compensating
    workflows.
-   Define a manual intervention process for unrecoverable cases.

### 6.4 Orchestration vs choreography

  -----------------------------------------------------------------------
  Aspect                  Orchestration           Choreography
  ----------------------- ----------------------- -----------------------
  Control                 Central coordinator     Services react to
                                                  events

  Workflow visibility     Explicit                Distributed across
                                                  services

  Complexity              Coordinator must be     Event interactions can
                          maintained              become difficult to
                                                  follow

  Best fit                Complex workflows with  Simple event reactions
                          branching and           and loosely coupled
                          compensation            flows
  -----------------------------------------------------------------------

Recommendation: use orchestration for complex business transactions that
require explicit state, deadlines, compensation, and operational
visibility. Use choreography for simple event reactions where a central
workflow coordinator would add unnecessary complexity.

------------------------------------------------------------------------

## 7. Messaging reliability patterns

### 7.1 Transactional Outbox

Avoid writing to the database and publishing to Kafka as two unrelated
operations inside a service method. A database commit can succeed while
message publication fails, or vice versa.

Instead, write the business change and an outbox record in the same
local database transaction.

``` text
One Local Database Transaction
       |
       +--> Save Order
       |
       +--> Save Outbox Event
                  |
                  v
           Outbox Publisher
                  |
                  v
                Kafka
                  |
                  v
             Consumer
```

The publisher can use polling or change-data capture. It must handle
retries and duplicate publication. The consumer must be idempotent
because delivery is generally at least once.

### 7.2 Inbox / Idempotent Consumer

Maintain a durable record of processed message IDs or equivalent
idempotency keys. The message's business effect and inbox record should
be committed atomically in the consumer's local database transaction.

### 7.3 Additional messaging standards

  -----------------------------------------------------------------------
  Pattern                             Purpose
  ----------------------------------- -----------------------------------
  Dead Letter Queue (DLQ)             Isolate messages that cannot be
                                      processed after defined retries

  Retry with backoff                  Recover from transient faults
                                      without overwhelming dependencies

  Schema Registry / schema governance Manage event schemas and
                                      compatibility

  Correlation ID                      Connect related messages and
                                      operations

  Event ID                            Identify an individual event

  Aggregate ID and version            Identify the source aggregate and
                                      sequence

  Consumer groups                     Scale event processing

  Partition key                       Preserve ordering for a chosen
                                      entity where required
  -----------------------------------------------------------------------

Do not assume global ordering across Kafka topics or partitions. Define
ordering requirements explicitly.

### 7.4 Example event envelope

``` json
{
  "eventId": "unique-event-id",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-11-17T10:00:00Z",
  "producer": "order-service",
  "correlationId": "workflow-correlation-id",
  "causationId": "causing-message-id",
  "aggregate": {
    "type": "Order",
    "id": "order-id",
    "version": 1
  },
  "payload": {
    "orderId": "order-id",
    "customerId": "customer-id"
  }
}
```

Treat this as an illustrative contract. Establish naming, compatibility,
privacy, and schema-validation rules for the actual platform.

------------------------------------------------------------------------

## 8. Resilience and fault tolerance

Standardise the following:

-   Explicit connection and request timeouts.
-   Bounded retries with exponential backoff and jitter.
-   Circuit breakers for unstable remote dependencies.
-   Bulkheads to isolate resource pools.
-   Rate limits and concurrency limits where appropriate.
-   Idempotency keys for externally retried operations.
-   Graceful degradation where the business permits it.
-   DLQ monitoring and replay procedures.
-   Health, readiness, and liveness endpoints.
-   Graceful shutdown and in-flight message handling.

Do not blindly retry non-idempotent operations. Retry policy must be
based on the operation's semantics and failure type.

------------------------------------------------------------------------

## 9. Technology standards

Choose versions based on current support windows and compatibility
across the Spring ecosystem.

  -----------------------------------------------------------------------
  Area                                Recommended options
  ----------------------------------- -----------------------------------
  Language                            Java 25 LTS, subject to
                                      framework compatibility

  Framework                           Supported Spring Boot release

  Build                               Maven or Gradle; standardise on one

  API                                 REST with OpenAPI

  Validation                          Jakarta Bean Validation

  Persistence                         Spring Data JPA or JDBC

  Migrations                          Flyway or Liquibase

  Messaging                           Apache Kafka; RabbitMQ where its
                                      model fits better

  Resilience                          Resilience4j

  Security                            Spring Security with OAuth2/OIDC

  Serialization                       Jackson or an explicitly
                                      standardised alternative

  Logging                             SLF4J with structured JSON output

  Tracing                             OpenTelemetry

  Metrics                             Micrometer and Prometheus

  Dashboards                          Grafana

  Logs                                Loki or ELK / OpenSearch

  Testing                             JUnit, Mockito, Testcontainers

  Architecture tests                  ArchUnit

  Contract tests                      Spring Cloud Contract or Pact

  Code quality                        SonarQube and static analysis

  Containers                          Docker

  Orchestration                       Kubernetes when operationally
                                      justified
  -----------------------------------------------------------------------

Avoid adopting every technology by default. Keep the platform's
supported stack documented and version-managed.

------------------------------------------------------------------------

## 10. Security standards

-   Use OAuth2/OIDC for service and user authentication where
    appropriate.
-   Enforce authorization at service boundaries, not only at the
    gateway.
-   Use least-privilege service identities and database accounts.
-   Store secrets in a secret manager; never commit secrets.
-   Encrypt traffic in transit and sensitive data at rest.
-   Validate all external input.
-   Apply secure defaults for CORS, headers, and token validation.
-   Avoid logging passwords, access tokens, payment data, or personal
    information.
-   Scan dependencies, container images, and source code.
-   Define key rotation and incident response procedures.
-   Audit security-sensitive operations.

------------------------------------------------------------------------

## 11. Observability and operational standards

Observability should be designed into each service.

### 11.1 Logs

Use structured logs with consistent fields:

-   `timestamp`
-   `level`
-   `service.name`
-   `environment`
-   `traceId`
-   `spanId`
-   `correlationId`
-   `sagaId` where applicable
-   `eventId` where applicable
-   `logger`
-   `message`
-   relevant, non-sensitive business identifiers

Use consistent log levels. Do not use logs as a substitute for metrics
or audit records.

### 11.2 Metrics

Track technical and business metrics.

Technical examples:

-   Request rate, error rate, and latency (RED).
-   CPU, memory, thread pools, and connection pools.
-   Kafka consumer lag and processing failures.
-   Database connection usage.
-   Retry and circuit-breaker activity.
-   Outbox backlog and age of oldest unpublished event.
-   Saga duration, failures, and stuck workflows.
-   CQRS projection lag.

Business metrics should be carefully defined with business owners and
should avoid high-cardinality labels.

### 11.3 Tracing

Use OpenTelemetry instrumentation and propagate trace context across
HTTP and messaging boundaries. Include correlation and Saga identifiers
where useful, but do not treat them as replacements for trace context.

### 11.4 Suggested platform

``` text
Services
  |
  +--> OpenTelemetry SDK / Agent
  |
  v
OpenTelemetry Collector
  |
  +--> Metrics --> Prometheus --> Grafana
  |
  +--> Traces  --> Tempo / Jaeger
  |
  +--> Logs    --> Loki / Elasticsearch
```

Choose a coherent observability stack and avoid maintaining overlapping
systems without a clear reason.

------------------------------------------------------------------------

## 12. Local microservices administration dashboard

Build a local dashboard that visualises the actual running platform and
operational telemetry. It should not be a hardcoded diagram that merely
claims services are healthy.

### Recommended modules

1.  **Service Overview**
    -   Service name, version, environment, health, readiness, and
        endpoint.
    -   Runtime status and dependency health.
    -   Links to logs, metrics, and traces.
2.  **Architecture Topology**
    -   Service-to-service relationships.
    -   HTTP and messaging communication paths.
    -   Kafka topics and consumer relationships.
    -   Visual distinction between synchronous and asynchronous
        communication.
3.  **Saga Monitor**
    -   Saga ID, business reference, current state, and elapsed time.
    -   Completed, active, failed, and compensating steps.
    -   Retry history, failure reason, and timestamps.
    -   Safe, audited retry or recovery actions where supported.
4.  **CQRS Monitor**
    -   Command and query activity.
    -   Read-model projection status and lag.
    -   Projection failures and replay status.
    -   Read/write data-source health.
5.  **Kafka Monitor**
    -   Topics, partitions, consumer groups, and consumer lag.
    -   Throughput, errors, and DLQ counts.
    -   Outbox publishing backlog.
6.  **Distributed Tracing**
    -   Search by trace ID, correlation ID, Saga ID, or service.
    -   View spans and cross-service latency.
    -   Identify slow or failing dependencies.
7.  **Logs Explorer**
    -   Search structured logs by service, time, trace ID, and
        correlation ID.
    -   Mask sensitive values.
    -   Link log entries to traces where possible.
8.  **Metrics and Alerts**
    -   Request rate, latency, error rate, resource use, and business
        metrics.
    -   Alert status and recent incidents.
9.  **Failure Demonstration / Simulation**
    -   Controlled failure injection in local development only.
    -   Demonstrate timeouts, retries, circuit breakers, duplicate
        events, DLQ routing, and Saga compensation.
    -   Never expose unrestricted failure injection in production.

Dashboard access must be authenticated when needed, protected from
dangerous actions, and based on real service APIs or telemetry sources.

------------------------------------------------------------------------

## 13. Multi-service repository structure

A monorepo is a practical starting point for a reference platform. Each
service should still have independent build, test, configuration, and
deployment boundaries.

``` text
enterprise-platform/
├── services/
│   ├── api-gateway/
│   ├── identity-service/
│   ├── customer-service/
│   ├── order-service/
│   ├── payment-service/
│   └── inventory-service/
│
├── shared/
│   ├── common-observability/
│   ├── common-security/
│   ├── common-messaging/
│   ├── common-error-handling/
│   └── common-test-support/
│
├── contracts/
│   ├── openapi/
│   ├── events/
│   └── schemas/
│
├── infrastructure/
│   ├── docker/
│   ├── kafka/
│   ├── prometheus/
│   ├── grafana/
│   ├── loki/
│   └── otel-collector/
│
├── deployment/
│   ├── kubernetes/
│   ├── helm/
│   └── environments/
│
├── admin-dashboard/
│
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── event-catalog/
│   └── runbooks/
│
├── scripts/
├── docker-compose.yml
└── README.md
```

### Shared library rules

Share stable technical capabilities, not business-domain models.

Good candidates:

-   Observability conventions.
-   Security integration helpers.
-   Messaging infrastructure utilities.
-   Standard error response support.
-   Test fixtures and test infrastructure.

Avoid:

-   Shared JPA entities.
-   Shared business aggregates.
-   A universal service layer.
-   Shared database access across services.
-   A common library that forces all services to upgrade together for
    unrelated changes.

Version shared libraries and preserve service autonomy.

------------------------------------------------------------------------

## 14. Testing strategy

``` text
src/test/java/
├── unit/
│   ├── domain/
│   ├── application/
│   └── saga/
├── integration/
│   ├── persistence/
│   ├── messaging/
│   └── api/
├── architecture/
├── contract/
├── component/
└── e2e/
```

### Required test categories

  -----------------------------------------------------------------------
  Test type                           What to verify
  ----------------------------------- -----------------------------------
  Domain unit tests                   Business invariants, state
                                      transitions, value objects

  Application unit tests              Command/query handler behaviour

  Saga tests                          Happy path, timeout, retry,
                                      compensation, recovery

  Persistence integration tests       Mappings, queries, transactions,
                                      migrations

  Messaging integration tests         Serialization, consumers, outbox,
                                      inbox, DLQ

  Contract tests                      API and event compatibility

  Architecture tests                  Dependency and package boundaries

  Component tests                     Service behaviour with real
                                      dependencies

  End-to-end tests                    Cross-service business workflows

  Security tests                      Authentication, authorization,
                                      input validation
  -----------------------------------------------------------------------

Use Testcontainers for realistic integration tests involving databases
and messaging infrastructure. Keep most tests fast and isolated; reserve
full distributed tests for critical workflows.

### Example ArchUnit rule

``` java
@ArchTest
static final ArchRule domainMustNotDependOnInfrastructure =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("..infrastructure..");
```

Add rules for presentation-to-domain access, forbidden framework
dependencies in domain packages, and cycles between packages.

------------------------------------------------------------------------

## 15. API and event contract standards

### REST APIs

-   Use resource-oriented URLs and consistent HTTP semantics.
-   Define request and response DTOs independently of persistence
    entities.
-   Validate inputs and return consistent error formats.
-   Support pagination and filtering for collection endpoints.
-   Document APIs using OpenAPI.
-   Define compatibility and deprecation policies.
-   Use idempotency keys for operations where clients may safely retry.
-   Apply authentication, authorization, and rate limits.

### Events

-   Use explicit event names and versions.
-   Include event ID, event type, occurrence time, producer, correlation
    ID, and causation ID.
-   Define aggregate identity and version when relevant.
-   Avoid leaking internal database schemas into event payloads.
-   Avoid sensitive data unless there is a clear, secured business
    requirement.
-   Establish schema compatibility rules and consumer ownership.
-   Document event producers, consumers, topics, retention, and replay
    expectations.

------------------------------------------------------------------------

## 16. Code quality and development standards

Establish repository-wide rules for:

-   Java naming conventions and formatting.
-   SOLID principles and clear class responsibilities.
-   Constructor injection; avoid field injection.
-   Immutable value objects where appropriate.
-   Explicit transaction boundaries.
-   Consistent exception handling.
-   No secrets or environment-specific configuration in source code.
-   No business logic in controllers or messaging listeners.
-   No direct cross-service database access.
-   No unbounded retries or unbounded in-memory queues.
-   No blocking calls on reactive event-loop threads.
-   No unnecessary abstraction or premature generic frameworks.
-   Code review and automated quality gates.

Use static analysis, dependency vulnerability scanning, formatting
checks, and architecture tests in CI.

------------------------------------------------------------------------

## 17. CI/CD and deployment standards

A service pipeline should generally perform:

1.  Compile and validate.
2.  Run unit tests.
3.  Run architecture tests.
4.  Run integration and contract tests.
5.  Run static analysis and quality gates.
6.  Scan dependencies and container images.
7.  Build an immutable container image.
8.  Publish versioned artifacts.
9.  Deploy to a non-production environment.
10. Run smoke tests and health checks.
11. Promote through controlled environments.
12. Support rollback or forward-fix procedures.

Use externalised configuration and environment-specific secrets. Apply
database migrations safely and design deployments to tolerate compatible
old/new application versions during rolling upgrades.

------------------------------------------------------------------------

## 18. Architecture documentation

Maintain the following documents:

``` text
docs/
├── architecture/
│   ├── system-context.md
│   ├── container-diagram.md
│   ├── service-catalog.md
│   ├── data-ownership.md
│   ├── messaging-topology.md
│   └── security-model.md
├── adr/
│   ├── 0001-use-ddd-and-hexagonal-architecture.md
│   ├── 0002-use-kafka-for-integration-events.md
│   ├── 0003-use-saga-orchestration-for-order-flow.md
│   └── 0004-use-transactional-outbox.md
├── api/
├── event-catalog/
└── runbooks/
    ├── service-unavailable.md
    ├── kafka-consumer-lag.md
    ├── dlq-reprocessing.md
    └── stuck-saga-recovery.md
```

Every significant architectural decision should have an ADR documenting
context, decision, alternatives, consequences, and status.

------------------------------------------------------------------------

## 19. Recommended implementation sequence

Build a working vertical slice first. Do not attempt to implement every
platform capability at once.

  -----------------------------------------------------------------------
  Phase                   Scope                   Expected outcome
  ----------------------- ----------------------- -----------------------
  1                       Service skeleton and    Consistent Spring Boot
                          engineering conventions service template

  2                       DDD and Hexagonal       Domain and application
                          Architecture            boundaries

  3                       REST, validation,       Functional service API
                          security, persistence   

  4                       CQRS                    Separate command and
                                                  query use cases

  5                       Kafka and event         Asynchronous service
                          contracts               communication

  6                       Outbox and Inbox        Reliable event
                                                  publication and
                                                  consumption

  7                       Saga orchestration      Persisted multi-service
                                                  workflow

  8                       Compensation and        Failure-safe business
                          recovery                workflow

  9                       OpenTelemetry and       Distributed traces and
                          metrics                 service metrics

  10                      Centralised logs and    Operational visibility
                          dashboards              

  11                      Docker and CI/CD        Repeatable build and
                                                  deployment

  12                      Architecture and        Enforced engineering
                          end-to-end quality      standards
                          gates                   
  -----------------------------------------------------------------------

### Suggested first vertical slice

Implement an order-placement workflow:

1.  Client submits an order.
2.  Order Service validates and persists a pending order.
3.  Order Service writes an event to its outbox.
4.  Saga orchestrator starts or advances the workflow.
5.  Inventory Service reserves stock.
6.  Payment Service authorises or processes payment.
7.  Order Service confirms the order.
8.  Query projection updates the order read model.
9.  Dashboard displays the Saga, events, traces, and metrics.
10. A controlled failure demonstrates retry and compensation.

Test both success and failure paths before adding more business
capabilities.

------------------------------------------------------------------------

## 20. Definition of done for a microservice

A service is not complete merely because its REST endpoints work. Before
considering it production-ready, verify:

### Architecture

-   [ ] Bounded context and ownership are documented.
-   [ ] Domain logic is independent of infrastructure.
-   [ ] Package boundaries are enforced by tests.
-   [ ] No cross-service database access exists.

### Application behaviour

-   [ ] Commands and queries have clear responsibilities.
-   [ ] Validation and error handling are consistent.
-   [ ] Transaction boundaries are explicit.
-   [ ] API and event contracts are documented.

### Distributed behaviour

-   [ ] Events are published reliably.
-   [ ] Consumers are idempotent.
-   [ ] Retry, timeout, and DLQ policies are defined.
-   [ ] Saga state is durable where Saga is used.
-   [ ] Compensation and recovery are tested.

### Operations

-   [ ] Structured logs include trace/correlation context.
-   [ ] Metrics and distributed traces are available.
-   [ ] Health and readiness checks work.
-   [ ] Alerts and operational runbooks exist.
-   [ ] Secrets and configuration are externalised.

### Quality and delivery

-   [ ] Unit, integration, contract, and architecture tests pass.
-   [ ] Security and dependency scans pass.
-   [ ] Container image is reproducible and versioned.
-   [ ] CI/CD pipeline is configured.
-   [ ] Deployment and rollback/recovery procedures are documented.

------------------------------------------------------------------------

## 21. Final architecture recommendation

Use the following combination as the platform's default:

  -----------------------------------------------------------------------
  Concern                             Recommended approach
  ----------------------------------- -----------------------------------
  Service boundaries                  Domain-Driven Design bounded
                                      contexts

  Internal structure                  Hexagonal Architecture with Clean
                                      Architecture dependency rules

  Use-case separation                 CQRS

  Cross-service workflows             Saga orchestration for complex
                                      workflows

  Service communication               REST for synchronous
                                      request/response; events for
                                      asynchronous integration

  Event reliability                   Transactional Outbox and Inbox

  Messaging                           Kafka where event streaming and
                                      replay are useful

  Persistence                         Database owned by each service

  Resilience                          Timeouts, bounded retries, circuit
                                      breakers, bulkheads

  Observability                       OpenTelemetry, Prometheus, Grafana,
                                      and centralised logs

  Testing                             JUnit, Testcontainers, contract
                                      tests, ArchUnit, and E2E tests

  Delivery                            Docker and automated CI/CD

  Operations                          Runbooks, dashboards, alerts, and
                                      recovery procedures
  -----------------------------------------------------------------------

The goal is not to maximise the number of patterns or technologies. The
goal is to enforce clear boundaries, reliable business workflows,
testable code, safe failure handling, and operational visibility while
keeping each service as simple as its domain allows.
