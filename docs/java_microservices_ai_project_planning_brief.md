# AI Project Planning Brief: Production-Grade Java Microservices Platform

## 1. Purpose

Act as a **principal software architect, senior Java engineer, platform
engineer, SRE, security engineer, and technical project manager**.

Create a practical, production-oriented project plan for building a
comprehensive microservices platform from the perspective of a Java
backend developer. The project must teach and demonstrate microservices
architecture, distributed systems, event-driven design, reliability,
security, observability, performance engineering, and cloud-native
deployment.

Do not merely produce a list of technologies or a tutorial sequence.
Produce an implementation-ready plan with architecture decisions,
milestones, deliverables, acceptance criteria, dependencies, risks,
testing strategy, and demonstrable outcomes.

The project should be realistic enough to serve as a portfolio-quality
reference implementation and as a learning environment for senior Java
developers moving towards staff engineer / solution architect
responsibilities.

## 2. Project concept

Build an **E-commerce Order Management and Fulfilment Platform**
composed of independently deployable services.

The system should support the lifecycle:

1.  A customer authenticates and browses products.
2.  Product details and availability are queried.
3.  A customer places an order.
4.  Inventory is reserved.
5.  Payment is simulated through a payment provider adapter.
6.  The order is confirmed or rejected.
7.  Notifications are emitted.
8.  Order status and history can be queried.
9.  Operational teams can observe, debug, and operate the system.

Use simulated payment processing; do not integrate real payment
credentials or move real money.

## 3. Architectural principles and constraints

Design around these principles:

-   Clear bounded contexts and business capabilities.
-   Each service owns its data and schema; no shared database tables
    across services.
-   Independent build, test, configuration, deployment, and scaling.
-   Prefer asynchronous events for workflows that do not require an
    immediate response.
-   Use synchronous APIs only where request-response semantics are
    appropriate.
-   Design for partial failure, duplicate delivery, delayed messages,
    retries, and service unavailability.
-   Make consistency guarantees explicit for each business workflow.
-   Prefer simple, supportable solutions over unnecessary infrastructure
    or patterns.
-   Avoid creating a distributed monolith through excessive synchronous
    service-to-service calls.
-   Keep the initial environment runnable locally using Docker Compose.
-   Treat Kubernetes as a later deployment milestone, not a prerequisite
    for basic development.
-   Use secure defaults, externalized configuration, and secret
    management.
-   Make the system observable from the beginning.
-   Document every significant architecture decision and its trade-offs.

Do not force every pattern into the system. For each proposed pattern or
technology, explain the problem it solves, why it is justified,
alternatives, and operational cost.

## 4. Proposed technology baseline

Treat these as preferred starting points, not unquestionable mandates.
Check current compatibility and select a coherent, supported version set
before implementation. Record exact versions in a version catalogue and
avoid mixing incompatible release trains.

### Backend and Java

-   Java 21 or a newer supported LTS version, subject to ecosystem
    compatibility.
-   Spring Boot.
-   Spring Security.
-   Spring Data JPA / Hibernate.
-   Spring Actuator.
-   Spring Kafka.
-   Spring Cloud Gateway where justified.
-   Spring Cloud Config only if centralized configuration is genuinely
    needed.
-   Resilience4j for resilience patterns.
-   Maven or Gradle; choose one and standardize.
-   Flyway or Liquibase for database migrations.

### APIs and contracts

-   REST and OpenAPI.
-   Spring RestClient or WebClient where appropriate.
-   OpenFeign only where its trade-offs are justified.
-   gRPC as an optional comparison or advanced exercise, not a default
    requirement.
-   Contract testing using Spring Cloud Contract or Pact.

### Messaging and data

-   Apache Kafka for domain events.
-   Schema Registry with Avro or Protobuf if schema governance is
    included.
-   PostgreSQL as the primary relational database.
-   Redis for caching and carefully scoped ephemeral coordination.
-   Elasticsearch or OpenSearch for a dedicated search use case, only if
    justified.
-   Debezium for CDC as an advanced extension, not a mandatory initial
    dependency.

### Identity and security

-   Keycloak as an identity provider.
-   OAuth 2.0 and OpenID Connect.
-   JWT validation and authorization in services.
-   Service-to-service authentication.
-   mTLS as an advanced deployment/security exercise.
-   Secret management appropriate to the target environment.

### Observability

-   Micrometer and Spring Boot Actuator.
-   OpenTelemetry instrumentation and context propagation.
-   Prometheus for metrics.
-   Grafana for dashboards.
-   Grafana Tempo or Jaeger for traces.
-   Loki or Elasticsearch-based centralized logging; choose one initial
    logging path.
-   Alertmanager or equivalent alerting.
-   Structured JSON logs with trace and correlation identifiers.

### Testing and quality

-   JUnit 5.
-   Mockito.
-   Testcontainers.
-   WireMock.
-   ArchUnit.
-   SonarQube or an equivalent static analysis tool.
-   OWASP dependency/security scanning.
-   k6 or Gatling for load testing.

### Delivery and runtime

-   Docker and Docker Compose.
-   Kubernetes.
-   Helm.
-   GitHub Actions or Jenkins; choose one CI/CD implementation.
-   Terraform as an infrastructure-as-code extension.
-   Argo CD as an optional GitOps extension.
-   Trivy or equivalent container/image scanning.

## 5. Service boundaries and responsibilities

Start with a domain decomposition exercise. Validate these boundaries
using DDD concepts before creating repositories or services.

Suggested bounded contexts:

### 5.1 API Gateway

Responsibilities: - External routing. - Authentication token validation
where appropriate. - Request correlation. - Rate limiting and
request-size controls. - API version routing. - No business logic or
direct database access.

### 5.2 Identity Provider

Use Keycloak for identity lifecycle and token issuance. Do not build a
custom authentication server.

Responsibilities: - User authentication. - OIDC flows. - Roles and
groups. - Client configuration. - Token lifecycle.

### 5.3 Customer Service

Responsibilities: - Customer profile and preferences. - Customer-facing
profile APIs. - Own customer data store.

### 5.4 Product Service

Responsibilities: - Product catalogue. - Product details, categories,
and pricing information. - Product lifecycle events. - Own product
database.

### 5.5 Inventory Service

Responsibilities: - Stock levels. - Reservations and reservation
expiry. - Inventory adjustments. - Idempotent reservation and release
operations. - Own inventory database.

### 5.6 Order Service

Responsibilities: - Order creation and lifecycle. - Order state
machine. - Order item snapshots and price snapshots. - Saga coordination
if orchestration is selected. - Order history and status APIs. - Own
order database.

### 5.7 Payment Service

Responsibilities: - Simulated payment authorization/capture/failure. -
Payment state and transaction references. - Idempotency keys. - Payment
events. - Provider adapter boundary to keep external provider
integration replaceable. - Own payment database.

### 5.8 Notification Service

Responsibilities: - Consume business events. - Simulate email/SMS/push
notifications. - Retry and dead-letter handling. - Notification
templates and delivery status where useful. - No synchronous dependency
in the order critical path unless justified.

### 5.9 Search Service (optional)

Responsibilities: - Search-optimized product projection. - Consume
product events or CDC. - Demonstrate read-model projection and eventual
consistency. - Do not become a second owner of product master data.

Avoid splitting services purely by technical layer. Reassess whether
Customer Service is required for the first vertical slice and keep
optional services out of the initial scope if they do not add learning
value.

## 6. Architecture patterns to evaluate and demonstrate

For each pattern, include: - Problem statement. - Context and
applicability. - Sequence or component diagram. - Implementation
approach. - Failure modes. - Tests proving the behavior. - Trade-offs
and alternatives.

Patterns to evaluate:

### Core architecture

-   Modular monolith versus microservices.
-   Domain-Driven Design.
-   Bounded Contexts.
-   Database per Service.
-   API Gateway.
-   Backend for Frontend (conceptual or optional implementation).
-   Anti-Corruption Layer.
-   Strangler Fig migration (documented scenario).

### Communication

-   Synchronous request-response.
-   Event-driven architecture.
-   Publish/subscribe.
-   Choreography versus orchestration.
-   API composition.
-   Event contracts and schema evolution.
-   Consumer groups and partitioning.

### Distributed data and transactions

-   Saga: choreography and orchestration comparison; implement one and
    document when to choose the other.
-   Transactional Outbox.
-   Idempotent consumer / Inbox pattern.
-   Eventual consistency.
-   CQRS where a real read/write separation exists.
-   Event sourcing as a documented comparison or optional extension, not
    a default.
-   CDC with Debezium as an advanced extension.
-   Compensating transactions.
-   Optimistic and pessimistic locking.

### Resilience

-   Timeout.
-   Retry with exponential backoff and jitter.
-   Circuit breaker.
-   Bulkhead.
-   Rate limiting.
-   Load shedding.
-   Graceful degradation.
-   Dead-letter queue.
-   Backpressure.
-   Health and readiness checks.

### Runtime and operations

-   Service discovery and load balancing.
-   Centralized configuration.
-   Rolling, blue-green, and canary deployment.
-   Horizontal scaling.
-   Autoscaling.
-   Graceful shutdown.
-   Disaster recovery and backup/restore.
-   SLI, SLO, SLA, and error budgets.

## 7. Required business workflows

Design and document at least these end-to-end workflows.

### Workflow A: Successful order

1.  Client submits an order with an idempotency key.
2.  Gateway authenticates and routes the request.
3.  Order Service validates the request and creates a PENDING order.
4.  Order Service writes the order and an outbox event atomically.
5.  An outbox publisher sends the order-created event to Kafka.
6.  Inventory Service consumes the event and reserves stock
    idempotently.
7.  Inventory emits reservation success or failure.
8.  Payment Service processes the simulated payment idempotently.
9.  Order state is updated based on the workflow result.
10. Notification Service consumes the resulting business event.
11. Client can query order status.

Specify the exact ordering and coordination strategy. Avoid assuming
that Kafka delivery, database updates, or downstream calls are atomic
across services.

### Workflow B: Inventory unavailable

-   Inventory rejects the reservation.
-   Order transitions to a meaningful failure/cancellation state.
-   No payment should be captured.
-   Notification is emitted.
-   Duplicate inventory events must not corrupt state.

### Workflow C: Payment failure after inventory reservation

-   Payment fails or times out.
-   Inventory reservation is released through a compensating action.
-   Order reaches a terminal failure state.
-   Compensation is retryable and idempotent.
-   The system handles the case where compensation itself temporarily
    fails.

### Workflow D: Duplicate and delayed events

-   Deliver the same event more than once.
-   Deliver events out of order where possible.
-   Delay a consumer.
-   Restart a consumer during processing.
-   Demonstrate idempotency, state transition validation, retry policy,
    and dead-letter handling.

### Workflow E: Downstream service unavailable

-   Stop or make a downstream service slow.
-   Demonstrate timeout, circuit breaker, bounded retries, and graceful
    degradation.
-   Show the impact in logs, metrics, traces, and dashboards.

## 8. Data design requirements

For every service, define: - Owned entities and aggregates. - Database
schema and migration strategy. - Primary keys and indexes. - Transaction
boundaries. - Data retention requirements. - Sensitive data
classification. - Read/write patterns. - Backup and restore approach. -
Event publication and consumption responsibilities.

Explain: - Why cross-service joins are avoided. - How read models are
populated. - How eventual consistency is exposed to API consumers. - How
schema changes remain backward compatible. - How duplicate commands and
events are handled. - How data repair and replay are performed safely.

Do not use a shared database schema as a shortcut. Local development may
use one PostgreSQL container with separate databases or schemas only if
ownership boundaries remain enforced and documented.

## 9. API and event contract requirements

For each REST API, define: - Method and path. - Request and response
examples. - Validation rules. - Authentication and authorization
requirements. - Error model. - Status codes. - Pagination and filtering
rules. - Idempotency behavior. - Versioning and compatibility policy.

For each event, define: - Event name and semantic meaning. - Event ID. -
Aggregate/entity ID. - Event type and schema version. - Occurred-at
timestamp. - Correlation ID and causation ID. - Producer and intended
consumers. - Partition key. - Delivery assumptions. - Retry and
dead-letter policy. - Compatibility rules.

Use an envelope standard consistently. Do not put secrets or unnecessary
personal information into events or logs.

## 10. Security requirements

Implement and test: - OIDC login flow using Keycloak. - JWT issuer,
audience, signature, and expiry validation. - Role-based
authorization. - Service-level authorization, not just gateway checks. -
Least-privilege service identities. - Secure secret injection. - TLS in
deployed environments. - Input validation and safe error responses. -
CORS policy appropriate to the client. - Rate limiting and abuse
protection. - Dependency and container vulnerability scanning. - Audit
logging for security-relevant actions.

Document the distinction between user identity propagation and service
identity. Never log access tokens, passwords, payment secrets, or
sensitive personal data.

## 11. Observability and production debugging requirements

Observability is a first-class deliverable, not a final add-on.

### Logs

-   Structured JSON logs.
-   Consistent service, environment, and version fields.
-   Trace ID, span ID, correlation ID, and request ID where applicable.
-   Appropriate log levels.
-   No secrets or sensitive payload dumps.
-   Centralized search and retention policy.

### Metrics

Create dashboards for: - Request rate. - Error rate. - Latency: P50,
P90, P95, P99. - JVM heap and non-heap usage. - Garbage collection
pauses and frequency. - CPU and thread counts. - HTTP connection and
server metrics. - Database connection pool utilization. - Database query
latency where instrumented. - Kafka consumer lag. - Message processing
rate and failures. - Retry counts. - Circuit breaker state. - Business
metrics: orders created, accepted, rejected, paid, and cancelled.

Avoid high-cardinality labels such as raw user IDs, order IDs, or
unbounded request paths.

### Distributed tracing

-   Instrument HTTP and Kafka paths.
-   Propagate trace context across service boundaries.
-   Demonstrate a trace spanning Gateway, Order, Inventory, and Payment.
-   Identify the slowest span and the source of failure.
-   Verify traces remain useful for asynchronous workflows.

### Alerts

Define actionable alerts for: - Elevated error rate. - High P95/P99
latency. - Service unavailable. - Kafka consumer lag beyond a defined
threshold. - Repeated payment/inventory failures. - Database pool
exhaustion. - JVM memory pressure. - SLO burn rate.

Provide runbooks for the most important alerts.

## 12. Testing strategy

Build a testing pyramid and define what each layer proves.

### Unit tests

-   Domain rules.
-   State machines.
-   Validation.
-   Retry and compensation decisions.
-   Mapping and utility logic.

### Integration tests

-   Repository and database behavior.
-   Kafka producer/consumer behavior.
-   Redis behavior.
-   Security configuration.
-   External provider adapters.

Use Testcontainers for real infrastructure dependencies where practical.

### Contract tests

-   Consumer/provider compatibility.
-   REST API compatibility.
-   Event schema compatibility.
-   Backward compatibility checks in CI.

### End-to-end tests

-   Successful order.
-   Inventory failure.
-   Payment failure and compensation.
-   Duplicate event delivery.
-   Service downtime and recovery.

### Non-functional tests

-   Load and stress testing.
-   Soak testing.
-   Resilience and fault injection.
-   Security scanning.
-   Performance regression checks.

For every important test, state the scenario, setup, expected result,
and evidence produced.

## 13. Performance engineering

Define measurable performance goals rather than claiming the system is
fast.

Evaluate: - JVM memory and garbage collection. - Threading and
concurrency. - Java virtual threads where appropriate and compatible
with dependencies. - Connection pool sizing. - Database indexes and
query plans. - N+1 queries. - Redis cache hit ratio and invalidation. -
Kafka partition count and consumer concurrency. - Payload size and
serialization. - HTTP connection reuse. - CPU and memory limits. -
Backpressure and queue growth.

Use k6 or Gatling to create reproducible workloads. Establish a baseline
before tuning and report before/after results. Do not invent benchmark
results.

## 14. Deployment and platform requirements

### Local development

-   One-command or clearly documented Docker Compose startup.
-   Health checks and dependency readiness.
-   Persistent local data where useful.
-   Sample environment configuration.
-   Seed data.
-   Clear startup and shutdown instructions.

### Kubernetes

-   Deployments, Services, ConfigMaps, Secrets, Ingress/Gateway
    integration.
-   Readiness, liveness, and startup probes.
-   Resource requests and limits.
-   Horizontal Pod Autoscaler where useful.
-   Pod disruption budgets.
-   Rolling update strategy.
-   Graceful termination.
-   Network policies where supported.
-   Helm charts.
-   Separate environment values.

### CI/CD

Pipeline should include: 1. Formatting and compilation. 2. Unit tests.
3. Integration and contract tests. 4. Static analysis. 5. Dependency and
security scanning. 6. Container image build. 7. Image scanning. 8.
Artifact publishing. 9. Deployment to a non-production environment. 10.
Smoke tests and rollout verification.

Explain rollback and database migration compatibility. Do not assume
application rollback can reverse an incompatible database migration.

## 15. Architecture documentation and diagrams

Create and maintain: - System context diagram. - Container/service
architecture diagram. - Deployment diagram. - Domain/bounded-context
map. - Service ownership and data ownership matrix. - Sequence diagrams
for the key workflows. - Saga state/compensation diagram. - Event flow
diagram. - Security/authentication flow. - Observability data flow. -
CI/CD flow. - Failure-mode diagrams.

Use C4 concepts where helpful. Store diagrams as editable source (for
example Mermaid or PlantUML) alongside rendered versions.

Create Architecture Decision Records (ADRs) for major choices,
including: - Service boundaries. - REST versus messaging. - Kafka event
format. - Saga orchestration versus choreography. - Outbox
implementation. - Database selection. - Observability stack. -
Authentication and authorization. - Deployment platform.

Each ADR should include context, decision, alternatives, consequences,
and status.

## 16. Suggested implementation milestones

Create a dependency-aware project plan. The following phases are a
suggested sequence; refine it based on technical dependencies and team
capacity.

### Milestone 0 --- Discovery and architecture

Deliver: - Requirements and use cases. - Domain decomposition. - Context
map. - Initial architecture diagrams. - ADRs. - Technology compatibility
matrix. - Repository strategy. - Risk register.

Exit criteria: - Service boundaries and ownership are reviewed. -
Critical workflows and consistency requirements are documented. - Scope
for the first vertical slice is agreed.

### Milestone 1 --- Platform foundation

Deliver: - Repository structure. - Shared conventions without creating a
shared business-logic library. - Build and dependency management. -
Local Docker Compose environment. - Initial Spring Boot services. -
Health checks and OpenAPI. - Database migrations.

Exit criteria: - Services start locally. - Health endpoints work. - Each
service owns its persistence boundary. - CI runs build and unit tests.

### Milestone 2 --- First end-to-end vertical slice

Deliver: - Product query. - Order creation. - Basic inventory
reservation. - Simulated payment. - Order status query.

Exit criteria: - A successful order can be completed end to end. - API
contracts and error handling are documented. - Integration tests cover
the main path.

### Milestone 3 --- Event-driven workflows

Deliver: - Kafka integration. - Domain event contracts. - Transactional
Outbox. - Idempotent consumers. - Saga workflow. - Retry and dead-letter
handling.

Exit criteria: - Duplicate events do not corrupt business state. -
Failure and compensation workflows pass automated tests. - Event
compatibility is checked.

### Milestone 4 --- Security

Deliver: - Keycloak integration. - OIDC and JWT validation. - Roles and
permissions. - Service-level authorization. - Secrets handling.

Exit criteria: - Unauthorized and insufficiently authorized requests are
rejected. - Tokens are validated correctly. - Sensitive data is not
exposed in logs.

### Milestone 5 --- Observability

Deliver: - Structured logs. - Metrics instrumentation. - Distributed
tracing. - Grafana dashboards. - Alert rules and runbooks.

Exit criteria: - A cross-service request can be traced. - Key service
and business metrics are visible. - At least one simulated failure can
be diagnosed from telemetry.

### Milestone 6 --- Reliability and performance

Deliver: - Timeouts, retries, circuit breakers, bulkheads. - Load tests
and baseline. - Database and JVM profiling. - Caching where justified. -
Failure injection.

Exit criteria: - Defined failure scenarios behave as designed. -
Performance findings and tuning decisions are recorded. - No unsupported
performance claims are made.

### Milestone 7 --- Kubernetes and CI/CD

Deliver: - Kubernetes manifests and Helm charts. - Environment
configuration. - CI/CD deployment pipeline. - Rollout and rollback
procedure. - Container security scanning.

Exit criteria: - Application deploys to a Kubernetes environment. -
Health probes and resource limits are configured. - Deployment and
rollback are demonstrated.

### Milestone 8 --- Advanced operations

Deliver: - SLOs and error budgets. - Alerting and runbooks. -
Backup/restore exercise. - Disaster recovery notes. - Optional chaos
engineering. - Architecture review and technical debt register.

Exit criteria: - Operational failure scenarios have documented
responses. - Recovery procedures are tested where feasible. - Final
architecture and trade-offs are explainable.

## 17. Repository and code organization

Recommend a repository strategy and explain the trade-offs between a
monorepo and multi-repository approach. For a learning project, a
monorepo may simplify local development and coordinated changes, but
services must remain independently buildable and deployable.

A possible layout:

``` text
microservices-platform/
  services/
    api-gateway/
    customer-service/
    product-service/
    inventory-service/
    order-service/
    payment-service/
    notification-service/
    search-service/
  contracts/
    openapi/
    events/
    schemas/
  platform/
    docker/
    compose/
    kubernetes/
    helm/
    observability/
    keycloak/
  tests/
    contract/
    end-to-end/
    performance/
  docs/
    architecture/
    adr/
    diagrams/
    runbooks/
    threat-model/
  scripts/
  .github/
    workflows/
  README.md
```

Do not create a giant shared library that couples service releases.
Shared libraries should be small, versioned, optional, and limited to
stable cross-cutting technical utilities.

## 18. Required deliverables from the AI planning agent

Before writing application code, produce the following in order:

1.  Executive summary and project scope.
2.  Explicit assumptions and open questions.
3.  Functional and non-functional requirements.
4.  Domain decomposition and bounded-context rationale.
5.  Service responsibility and data ownership matrix.
6.  System context and container diagrams.
7.  Detailed service architecture.
8.  End-to-end sequence diagrams.
9.  Event catalogue and API contract catalogue.
10. Data model outline per service.
11. Security and threat model.
12. Observability architecture and dashboard specification.
13. Testing strategy and test matrix.
14. Local development architecture.
15. Kubernetes and CI/CD architecture.
16. Milestone plan with dependencies and exit criteria.
17. Task backlog broken into epics, stories, and engineering tasks.
18. Risks, trade-offs, and architecture decision records.
19. Definition of Done and quality gates.
20. A phased implementation plan that can be executed incrementally.

For each backlog item, include: - Unique ID. - Title. -
Business/technical purpose. - Description. - Dependencies. - Acceptance
criteria. - Test requirements. - Expected files or components
affected. - Complexity estimate (relative sizing is acceptable). - Risks
and assumptions.

Do not fabricate precise calendar estimates without knowing team size,
availability, and experience. Provide relative estimates and identify
the information needed for a realistic schedule.

## 19. Definition of Done

A feature is not complete merely because the endpoint works.

A task is complete when applicable criteria are met: - Code follows
agreed conventions. - Unit and integration tests pass. - API/event
contracts are documented and compatible. - Authorization is implemented
and tested. - Errors are handled predictably. - Logs, metrics, and
traces are useful. - No secrets or sensitive data are leaked. - Database
changes are versioned. - Retry/idempotency behavior is addressed where
relevant. - Health and readiness behavior is correct. - Container build
succeeds. - Security and quality checks pass. - Operational
documentation is updated. - Acceptance criteria are demonstrated.

## 20. How the AI agent should work

Operate in **planning-first, approval-gated mode**.

1.  Inspect the existing repository and identify current code,
    conventions, tools, and constraints before proposing changes.
2.  If no repository exists, propose the structure and bootstrap plan.
3.  Ask only questions that materially affect architecture or scope.
    Clearly label assumptions when proceeding without answers.
4.  Do not start implementing the entire platform immediately.
5.  Present the architecture and milestone plan for review first.
6.  Identify the smallest valuable vertical slice.
7.  Implement one milestone at a time after approval.
8.  Before each milestone, state scope, files/services affected,
    dependencies, risks, and acceptance criteria.
9.  After each milestone, run relevant tests and report actual results.
10. Never claim tests, deployments, benchmarks, or integrations
    succeeded unless they were actually executed.
11. Keep architecture diagrams, ADRs, API/event contracts, and runbooks
    synchronized with implementation.
12. Prefer understandable, maintainable code over clever abstractions.
13. Avoid speculative abstractions and premature optimization.
14. Explain important architectural decisions as if mentoring a senior
    Java developer.
15. When introducing a technology, explain its purpose, configuration,
    failure modes, operational burden, and alternatives.
16. Flag when a proposed design creates tight coupling, excessive
    synchronous calls, shared data ownership, or unnecessary complexity.

## 21. Initial response expected from the AI

In its first response, do not generate the full application.

Instead, provide:

-   A concise understanding of the project.
-   The recommended target architecture.
-   A proposed service decomposition with justification.
-   The key architecture patterns and where they will be used.
-   A technology/version compatibility plan.
-   A phased roadmap and dependency map.
-   The first vertical slice.
-   Key risks and unresolved decisions.
-   A short list of essential clarification questions, only where
    needed.

Then wait for approval before implementation.

## 22. Success criteria

The finished project should allow a developer to demonstrate, with
working code and evidence, that they can:

-   Decompose a business domain into suitable services.
-   Design APIs and event contracts.
-   Implement synchronous and asynchronous communication.
-   Build reliable distributed workflows.
-   Handle duplicate messages and partial failures.
-   Maintain service-owned data and eventual consistency.
-   Secure APIs and service interactions.
-   Instrument and debug a distributed request.
-   Build meaningful dashboards and alerts.
-   Test service integrations and contracts.
-   Measure and improve performance.
-   Containerize, deploy, scale, and operate services.
-   Explain architecture trade-offs and operational consequences.

**Final instruction:** Optimize for depth of understanding and
demonstrable engineering quality, not the number of microservices or
technologies. A smaller, reliable, observable, well-tested system is
better than a large collection of shallow services.
