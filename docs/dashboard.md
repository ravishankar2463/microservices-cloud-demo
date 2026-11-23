# Build a Local Microservices Control Center – Architecture Visualization & Operations Dashboard

## 1. Objective

Design and implement a comprehensive, locally hosted administrative dashboard for the Java Microservices Platform.

The dashboard must provide complete visibility into the running microservices ecosystem.

It should allow developers to visually understand:

- Which microservices are running.
- What each service is responsible for.
- How services communicate with one another.
- Where incoming requests are routed.
- How events travel through Kafka.
- Which databases belong to which services.
- How distributed transactions and Saga workflows execute.
- What happens when a service fails.
- How retries, circuit breakers and compensating transactions work.
- How the application is performing.
- Where errors originate.
- How distributed traces connect different services.
- What happens internally when a user performs a business operation.

**The dashboard is intended to be an educational, architectural and operational control center, not merely a collection of CRUD screens or monitoring charts.**

A developer should be able to open the dashboard and understand the entire microservices ecosystem without manually inspecting every service's logs.

---

## 2. Technology Stack

Use the following preferred stack:

### Frontend
- Angular (latest compatible stable version).
- TypeScript.
- Angular Material or a modern component library.
- RxJS.
- Apache ECharts or Chart.js for visualizations.
- Cytoscape.js or React Flow equivalent compatible with Angular for interactive architecture diagrams.
- Tailwind CSS if useful.
- WebSocket or Server-Sent Events for live updates.

### Backend
Create a dedicated Spring Boot Admin/Management Service.

Preferred technologies:
- Java 21 or newer compatible LTS.
- Spring Boot.
- Spring Security.
- Spring Web.
- Spring WebSocket or SSE.
- Spring Actuator integration.
- Micrometer.
- OpenTelemetry integration.
- PostgreSQL for persistent dashboard metadata if needed.

### Infrastructure integrations
- Docker / Docker Compose.
- Kubernetes (optional advanced mode).
- Kafka.
- Redis.
- PostgreSQL.
- Prometheus.
- Grafana.
- OpenTelemetry.
- Grafana Tempo or Jaeger.
- ELK or Loki.

Do not create a second monitoring infrastructure unnecessarily. Integrate with the infrastructure already implemented in the platform.

---

## 3. Dashboard Architecture

Create a dedicated Management Service that aggregates operational information from the infrastructure and microservices.

The dashboard must not directly query individual service databases.

Use the following architecture:

Browser Dashboard
    |
    v
Management Service / BFF
    |
    +---- Spring Boot Actuator endpoints
    |
    +---- Prometheus API
    |
    +---- Grafana / Tempo APIs
    |
    +---- Kafka Admin APIs
    |
    +---- Docker Engine API (local mode)
    |
    +---- Kubernetes API (optional)
    |
    +---- Application Management APIs
    |
    +---- Centralized Logging API

The management service should normalize information into a consistent dashboard-facing model.

Important constraints:

- Management Service must not become a business-service dependency.
- Business transactions must continue if the dashboard is unavailable.
- Dashboard polling and monitoring must not overload application services.
- Use read-only access to operational infrastructure wherever possible.
- All administrative operations must be authenticated and authorized.
- Never expose unrestricted Docker socket access to the browser.
- Never expose secrets, tokens or sensitive environment variables.
- Distinguish actual telemetry from simulated or unavailable data.
- Never fabricate service health, request flows or performance metrics.

---

## 4. Dashboard UI Design

Build a modern, professional, dark-first developer console.

Design inspiration:
- Grafana.
- Datadog.
- Kubernetes Dashboard.
- AWS Console.
- Linear.
- Modern cloud infrastructure management consoles.

The UI should have:

- Dark and light themes.
- Responsive layout.
- Persistent sidebar navigation.
- Top navigation with environment selection.
- Live system status.
- Global search.
- Time range selector.
- Refresh controls.
- Notifications and alerts.
- Service/environment filters.
- Clear loading, empty, stale and error states.

Avoid excessive cards, unnecessary gradients and decorative charts.

Prioritize information density, usability and visual clarity.

---

## 5. Dashboard Modules

### Module 1: System Overview

Create the landing page showing the complete system status.

Display:

- Total microservices.
- Running services.
- Unhealthy services.
- Stopped services.
- Total requests per second.
- Average response time.
- P95 and P99 latency.
- Overall error rate.
- Kafka messages per second.
- Kafka consumer lag.
- Database connectivity.
- Redis connectivity.
- CPU usage.
- Memory utilization.
- JVM heap utilization.
- Active alerts.

Provide a live service status summary.

Every metric must show:
- Actual value.
- Unit.
- Time window.
- Data source.
- Last updated timestamp.

Clicking a metric should navigate to its detailed view.

### Module 2: Interactive Architecture Explorer

This is one of the most important modules.

Create an interactive architecture diagram showing:

- API Gateway.
- Customer Service.
- Product Service.
- Order Service.
- Inventory Service.
- Payment Service.
- Notification Service.
- Search Service.
- Keycloak.
- Kafka.
- Redis.
- PostgreSQL databases.
- Prometheus.
- Grafana.
- OpenTelemetry Collector.
- Tempo / Jaeger.
- Logging infrastructure.

Show communication relationships using different visual representations.

For example:

- Solid arrows: synchronous REST communication.
- Dashed arrows: asynchronous messaging.
- Database connections: persistence relationships.
- Different visual treatment: infrastructure dependencies.

Each service should be clickable.

When selected, show a detailed side panel containing:

- Service name.
- Business responsibility.
- Port and base URL.
- Current health.
- Version.
- Dependencies.
- Incoming connections.
- Outgoing connections.
- Database ownership.
- Kafka topics consumed.
- Kafka topics produced.
- Request rate.
- Error rate.
- Response latency.
- JVM metrics.
- Recent errors.
- Related distributed traces.

Provide filters to show:

- All services.
- Only synchronous communication.
- Only asynchronous communication.
- Database relationships.
- Infrastructure dependencies.
- Unhealthy services.

Support zooming, panning, dragging and resetting the architecture view.

### Module 3: Distributed Transaction Journey (Merged into Operations Console)

The request flow visualizer and distributed transaction journey are unified directly into the **Live Operations & Transaction Workflow Console** (see Section 6).

This interactive pipeline visualizes each architectural phase of an end-to-end distributed transaction:
1. **Client Request & Gateway Authentication**: `POST /api/orders` validated via Keycloak, rate-limited, and injected with `X-Correlation-ID`.
2. **ACID Write & Transactional Outbox**: Order persisted in PostgreSQL and relayed to Kafka topics & AWS SQS.
3. **Inventory Reservation & Concurrency Lock**: Redis distributed mutex lock `SET NX PX` and stock decrement guard on Java 21 Virtual Threads.
4. **Payment Gateway Authorization**: Simulated payment capture with idempotency keys and decline triggers.
5. **Saga Completion & Document Archival**: Order confirmed or rolled back; PDF invoices generated and archived to LocalStack S3.

The journey pipeline connects directly to live cluster execution presets and custom single-request dispatches, dynamically synchronizing node states (idle standby, running, completed, or compensated).

### Module 4: Service Registry and Health Monitor

Display all registered microservices in a searchable table.

Columns:

- Service.
- Environment.
- Instance ID.
- Version.
- Status.
- Host/container.
- Port.
- CPU.
- Memory.
- Uptime.
- Last health check.
- Dependencies.

Service details should provide:

- Health endpoint status.
- Liveness.
- Readiness.
- Application information.
- JVM information.
- Thread information.
- Connection pool metrics.
- Recent errors.
- Dependency health.

Support instance-level visibility if multiple replicas exist.

### Module 5: Kafka and Event Explorer

Provide a comprehensive Kafka monitoring interface.

Display:

- Kafka brokers.
- Topics.
- Partitions.
- Replication information.
- Consumer groups.
- Consumer lag.
- Messages produced.
- Messages consumed.
- Failed processing.
- Dead-letter topics.

For each topic, display:

- Topic name.
- Producer services.
- Consumer services.
- Partition count.
- Message rate.
- Consumer groups.
- Latest observed activity.

Create an event-flow visualization.

Example:

Order Service
  |
  v
order.created
  |
  +----> Inventory Service
  |
  +----> Payment Service
  |
  v
payment.completed
  |
  v
Order Service

Allow developers to inspect safe event metadata:

- Event ID.
- Event type.
- Schema version.
- Correlation ID.
- Causation ID.
- Producer.
- Consumer.
- Timestamp.
- Processing status.

Do not expose sensitive payload fields.

Provide a message inspection view with masking and configurable field allowlists.

### Module 6: Distributed Transaction and Saga Explorer

This module should explain how distributed transactions execute.

Display Saga workflows visually.

Example:

Order Created
    |
    v
Inventory Reserved
    |
    v
Payment Processing
    |
    v
Order Confirmed

Failure example:

Order Created
    |
    v
Inventory Reserved
    |
    v
Payment Failed
    |
    v
Release Inventory
    |
    v
Order Cancelled

For every Saga execution display:

- Saga ID.
- Correlation ID.
- Current state.
- Started timestamp.
- Completed timestamp.
- Participating services.
- Completed steps.
- Failed steps.
- Retry attempts.
- Compensation actions.
- Final status.

Allow developers to open an individual Saga execution and inspect its complete timeline.

Clearly distinguish forward actions from compensating actions.

### Module 7: Distributed Tracing Explorer

Integrate with OpenTelemetry and Tempo or Jaeger.

Provide:

- Trace search.
- Trace ID lookup.
- Service filtering.
- Time range.
- Error-only filtering.
- Duration filtering.
- Slow request detection.

Show waterfall visualizations containing:

- Parent and child spans.
- Service names.
- Operation names.
- Start times.
- Duration.
- Status.
- Error details.
- Downstream dependencies.

Allow navigation from a trace to the associated service and request flow.

### Module 8: Centralized Log Explorer

Integrate with the selected logging platform.

Features:

- Search by service.
- Search by trace ID.
- Search by correlation ID.
- Search by request ID.
- Filter by log level.
- Time range filtering.
- Error-only mode.
- Structured JSON viewer.

Show logs in chronological order.

Allow navigation from a log entry to its distributed trace when available.

Mask sensitive information and prevent unrestricted access to secrets.

### Module 9: Metrics and Performance

Create a technical metrics dashboard.

Include:

#### JVM
- Heap memory.
- Non-heap memory.
- Garbage collection.
- Thread count.
- CPU utilization.
- Class loading.

#### HTTP
- Request throughput.
- Error rate.
- P50/P95/P99 latency.
- Active requests.
- Connection usage.

#### Database
- Connection pool utilization.
- Active connections.
- Idle connections.
- Query latency where available.
- Connection acquisition failures.

#### Kafka
- Producer throughput.
- Consumer throughput.
- Consumer lag.
- Failed messages.
- Retry counts.

#### Business metrics
- Orders created.
- Orders completed.
- Orders failed.
- Payments successful.
- Payments failed.
- Inventory reservation failures.

Provide service-level comparisons and time-series charts.

### Module 10: Resilience and Failure Explorer

Create a dedicated area to understand resilience mechanisms.

Display:

- Circuit breaker status.
- Retry attempts.
- Timeout counts.
- Bulkhead utilization.
- Rate-limiting events.
- Failed downstream calls.
- Dead-letter messages.

Show circuit breaker states:

- CLOSED.
- OPEN.
- HALF_OPEN.

Display state transition history and relevant metrics.

Provide educational explanations for why a circuit breaker opened and what traffic is affected.

Any failure injection functionality must be disabled by default and restricted to a local development environment.

### Module 11: Database Explorer

Display database infrastructure and ownership.

Show:

- Service-to-database mapping.
- Database connectivity.
- Database health.
- Connection pool metrics.
- Migration version.
- Database size where available.
- Slow query indicators where available.

Provide read-only operational visibility.

Do not expose unrestricted SQL execution or direct cross-service database browsing.

### Module 12: Container and Infrastructure Monitor

Integrate with Docker Compose initially.

Display:

- Containers.
- Container status.
- CPU usage.
- Memory usage.
- Restart count.
- Uptime.
- Published ports.
- Health checks.

Allow developers to inspect container logs through a controlled backend integration.

Optional Kubernetes mode should provide:

- Namespaces.
- Pods.
- Deployments.
- Replica counts.
- Restarts.
- Resource utilization.
- Events.
- Readiness and liveness status.

Do not make Kubernetes mandatory for local development.

### Module 13: Architecture Learning Center

Every major architectural pattern used by the platform should have an educational explanation.

Examples:

- Why use an API Gateway?
- Why use Kafka instead of synchronous REST calls?
- Why does each microservice own its database?
- What is eventual consistency?
- Why is Saga necessary?
- What does the Outbox Pattern solve?
- Why do we need idempotent consumers?
- What happens when a circuit breaker opens?
- Why do we need distributed tracing?
- What is the difference between logs, metrics and traces?

Each explanation should include:

1. Problem statement.
2. Architectural concept.
3. Why the project uses it.
4. Where it is implemented.
5. Related services.
6. Interactive diagram.
7. Real execution example.
8. Trade-offs.
9. Failure scenarios.
10. Relevant source code references.

The objective is to make the dashboard an interactive microservices learning environment.

Features:
- Responsive catalog card grid with category filters and instant live search.
- Unconstrained viewport scrolling with flexbox overflow isolation (`min-height: 0`) and bottom buffer clearance ensuring all 12 cards remain fully visible without clipping across all font-scaling zoom ratios (80% to 150%).
- Decoupled slide-out inspection drawer with deep-linking to host editors (VS Code / Notepad).
- End-of-catalog status bar with a smooth "Back to Top ⬆" navigation shortcut.

---

## 6. Live Operations & Transaction Workflow Console

Provide a unified operations console and live transaction workflow hub for local development and demonstration.

This console consolidates the **Distributed Transaction Journey (Order Placement & Saga Execution)** with live scenario presets and an interactive HTTP request builder:

### Features:
- **Interactive Distributed Transaction Stepper Pipeline**:
  - Connects 5 core lifecycle phases: Gateway Auth -> ACID Outbox Write -> Inventory Mutex Lock -> Payment Authorization -> Saga Archival & S3.
  - Dynamically updates with live cluster telemetry (green for completed, red for failed, amber for compensated/rollback).
  - Quick action buttons on each node to jump to live logs, topology, and preset tests.
  - Collapsible/expandable header with manual pipeline reset capability.
- **Scenario Presets Grid**:
  - *🟢 Happy Path E-Commerce Flow*: Order placement, stock reservation, payment capture, and S3 PDF archiving.
  - *🔴 Payment Decline & Saga Rollback*: Over-limit order triggering payment failure and automated inventory compensation.
  - *🟠 Inventory Exhaustion Test*: Overstock order testing guardrail validation before payment invocation.
  - *🔵 Customer Order Cancellation*: Manual cancellation emitting Kafka events to release reserved stock.
- **Interactive Live Request Builder & Inspector**:
  - Dispatch custom single HTTP requests to any microservice (`api-gateway`, `order-service`, `inventory-service`, `payment-service`, `notification-service`).
  - Generates executable `curl` commands and inspects before/after infrastructure state deltas (PostgreSQL, Redis, S3).
- **Live Execution Trace Panel**:
  - Step-by-step timeline of executed cluster operations with correlation IDs, latency metrics, and direct links to Pod logs.

---

## 7. Real-Time Updates

The dashboard should support live updates using WebSocket or SSE.

Examples:

- Service goes DOWN.
- Service becomes HEALTHY.
- New order is created.
- Kafka event is published.
- Consumer processes an event.
- Saga progresses.
- Circuit breaker opens.
- Error occurs.
- New distributed trace becomes available.

Avoid excessively aggressive polling.

Use configurable refresh intervals, server-side aggregation, caching and event-driven updates where appropriate.

Clearly show the last successful refresh and stale data indicators.

---

## 8. Backend API Design

Create dedicated dashboard-facing APIs.

Possible endpoints:

GET /api/admin/overview
GET /api/admin/services
GET /api/admin/services/{serviceId}
GET /api/admin/architecture
GET /api/admin/dependencies
GET /api/admin/requests
GET /api/admin/events
GET /api/admin/kafka/topics
GET /api/admin/kafka/consumer-groups
GET /api/admin/sagas
GET /api/admin/sagas/{sagaId}
GET /api/admin/traces
GET /api/admin/logs
GET /api/admin/metrics
GET /api/admin/resilience
GET /api/admin/databases
GET /api/admin/containers
GET /api/admin/alerts

POST /api/admin/operations/refresh
POST /api/admin/operations/demo-workflow
POST /api/admin/operations/restart-container

These are proposed contracts. Refine them based on the actual architecture.

Requirements:

- Consistent response structure.
- Pagination.
- Filtering.
- Validation.
- Authentication and authorization.
- Rate limiting.
- Audit logging for administrative operations.
- Clear error responses.
- API documentation.

Do not create endpoints that simply return hardcoded demonstration data while claiming to represent real infrastructure.

---

## 9. Demo and Educational Mode

Provide a dedicated demo mode.

It should allow a developer to trigger predefined scenarios:

1. Successful order.
2. Inventory unavailable.
3. Payment failure.
4. Duplicate Kafka event.
5. Delayed Kafka consumer.
6. Inventory service unavailable.
7. Payment service timeout.
8. Circuit breaker opening.
9. Retry and recovery.
10. Saga compensation.

When a scenario is executed, the dashboard should visually explain:

- What was triggered.
- Which service received the request.
- Which event was published.
- Which service consumed it.
- What state changed.
- Whether a retry occurred.
- Whether compensation was required.
- How the workflow completed.

Demo-generated activity must be explicitly identified and distinguishable from ordinary application traffic.

---

## 10. Security Requirements

The dashboard is an administrative application.

Implement:

- Keycloak authentication.
- OIDC.
- JWT validation.
- Role-based access control.
- Read-only viewer role.
- Administrator role.
- Restricted local operator role.
- Audit trail for administrative operations.
- Session expiration.
- CSRF protection where applicable.
- Secure cookie configuration.
- Rate limiting.
- Sensitive field masking.

Never expose:
- Passwords.
- JWT tokens.
- Database credentials.
- Environment secrets.
- Private keys.
- Unmasked sensitive customer data.

The browser must never communicate directly with infrastructure management APIs using privileged credentials.

---

## 11. UX and Navigation

Suggested sidebar:

1. Overview
2. Architecture Explorer
3. Live Request Flow
4. Services
5. Kafka & Events
6. Saga Explorer
7. Distributed Tracing
8. Logs
9. Metrics & Performance
10. Resilience
11. Databases
12. Containers
13. Alerts
14. Learning Center
15. Operations Console
16. Settings

The interface must have consistent navigation between related objects.

For example:

Service → Recent Requests → Trace → Logs → Related Kafka Events → Saga Execution.

The developer should be able to follow a complete business transaction across the entire ecosystem without losing context.

---

## 12. Implementation Phases

### Phase 1: Dashboard Foundation
- Angular application.
- Spring Boot Management Service.
- Authentication.
- Navigation and layout.
- Service registry.
- System overview.
- Basic health information.

### Phase 2: Architecture Visualization
- Interactive architecture graph.
- Service dependencies.
- Database ownership.
- Synchronous and asynchronous relationships.
- Service details.

### Phase 3: Observability Integration
- Prometheus.
- Grafana.
- OpenTelemetry.
- Tempo / Jaeger.
- Centralized logs.
- Metrics dashboards.

### Phase 4: Messaging and Workflow Visibility
- Kafka topics.
- Consumer groups.
- Event explorer.
- Saga explorer.
- Correlation IDs.
- Event timeline.

### Phase 5: Live Operations
- Real-time updates.
- Container monitoring.
- Controlled local operations.
- Service restart functionality.
- Log inspection.

### Phase 6: Educational and Demo Experience
- Architecture Learning Center.
- Predefined demonstration scenarios.
- Failure simulation.
- Request journey visualization.
- Saga walkthrough.

### Phase 7: Hardening
- Performance optimization.
- Security review.
- Authorization tests.
- UI responsiveness.
- Error and stale-data handling.
- Integration testing.

---

## 13. Quality and Acceptance Criteria

The dashboard is complete only when:

1. It discovers or registers actual services.
2. It displays actual service health.
3. It identifies real service dependencies.
4. It displays real metrics from Prometheus.
5. It displays real distributed traces.
6. It searches real centralized logs.
7. It displays real Kafka topics and consumer groups.
8. It visualizes actual event activity.
9. It displays actual Saga executions from instrumented workflow data.
10. It identifies unavailable infrastructure components.
11. It distinguishes live, stale, unavailable and simulated data.
12. It supports navigation between services, traces, logs and events.
13. It protects administrative functionality.
14. It does not become a dependency for business transactions.
15. It can be started locally through documented commands.
16. It has automated tests for its backend integrations and important UI workflows.

For every feature, provide evidence of actual integration. Do not substitute static JSON or mock responses for production-like functionality.

---

## 14. AI Agent Implementation Instructions

Before implementing:

1. Inspect the existing microservices project.
2. Identify available services, ports, Actuator endpoints and infrastructure.
3. Inspect the existing observability stack.
4. Reuse existing Prometheus, Grafana, Kafka, tracing and logging infrastructure.
5. Identify missing telemetry instrumentation.
6. Define the dashboard data model.
7. Create the frontend and backend architecture.
8. Prepare the implementation backlog.

Do not blindly recreate infrastructure that already exists.

Do not modify business services unnecessarily just to satisfy dashboard requirements. Where instrumentation is missing, propose small, well-defined changes to the relevant service.

Implement incrementally:

- Start with the system overview.
- Then implement architecture visualization.
- Then connect real telemetry.
- Then add request/event/Saga exploration.
- Finally implement controlled operations and educational scenarios.

After every phase:

- Build the frontend.
- Build the backend.
- Run automated tests.
- Verify actual integrations.
- Document configuration.
- Update architecture diagrams.
- Report completed features and known limitations.

Never mark an integration complete until it has been tested against the actual running infrastructure.

## 15. Final Product Vision

The finished application should feel like a miniature internal engineering platform used by a team operating a distributed production system.

A developer should be able to:

- Open the dashboard.
- See every running microservice.
- Understand the entire architecture visually.
- Trigger an order.
- Watch the request travel across services.
- Watch Kafka events being published and consumed.
- Inspect Saga execution.
- See distributed traces.
- Find associated logs.
- Observe metrics.
- Simulate a failure.
- Understand how resilience mechanisms respond.
- Learn why each architectural decision was made.

**The primary goal is complete architectural transparency: show what is happening, where it is happening, how it is happening, why it is happening, and what happens when something goes wrong.**