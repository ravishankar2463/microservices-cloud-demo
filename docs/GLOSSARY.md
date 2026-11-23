# Ubiquitous Language & Domain Glossary (GLOSSARY.md)

This document establishes the ubiquitous language (per Domain-Driven Design and Matt Pocock engineering principles) for the Microservices Platform and Control Center.

---

## 1. Operations & Distributed Transaction Domain

### Distributed Transaction Journey
The unified 5-stage architectural lifecycle and real-time visualization of an e-commerce order:
1. **Gateway & OAuth2**: Client authentication via Keycloak, Token-Bucket rate limiting, and `X-Correlation-ID` injection on API Gateway (8080).
2. **ACID Outbox Write**: Atomic persistence of the `Order` aggregate and `OutboxEvent` in PostgreSQL (8081) with automated relay to Kafka and AWS SQS.
3. **Inventory Mutex Lock**: Concurrency-safe stock reservation using Redis distributed locks (`SET lock:stock:{sku} NX PX 5000`) on Java 21 Virtual Threads (8082).
4. **Payment Gateway Authorization**: Idempotent payment processing with amount-based decline rule triggers in Payment Service (8084).
5. **Saga Completion & Document Archival**: Order status confirmation, PDF invoice generation, and LocalStack S3 document archiving in Notification Service (8083).

### Operations Console
The consolidated operational hub (`view-operations`) in the Control Center (Port 8090) that integrates the Distributed Transaction Journey pipeline, live scenario presets, interactive request builder, and execution telemetry trace with real cluster state diffing.

### Saga Orchestration
A distributed transaction management pattern where an orchestrator (`OrderSagaOrchestrator`) coordinates forward steps and triggers automated compensating rollbacks across microservices upon downstream failures.

### Transactional Outbox Pattern
Writing business domain entities and outbox events in the same atomic ACID database transaction in PostgreSQL, followed by an asynchronous polling/CDC relay to Kafka to eliminate dual-write inconsistencies.

### Compensating Transaction
An operational rollback action executed by downstream services (e.g., releasing reserved stock in Redis and marking order as `CANCELLED`) when payment fails or is declined.

### Concurrency Mutex Lock
A distributed key-value lock in Redis (`SET lock:stock:{sku} NX PX 5000`) ensuring serialized stock reservation and preventing race conditions across horizontally scaled service replicas.

### Operational Flow Presets
Pre-configured, one-click end-to-end executable cluster scenarios:
- **🟢 Happy Path E-Commerce Flow**: Standard order ($149.00) completing all 5 steps with S3 invoice archiving.
- **🔴 Payment Decline & Saga Rollback**: High-value order ($5,000.00) failing card auth (HTTP 402) and triggering automated inventory compensation.
- **🟠 Inventory Exhaustion Test**: Overstock order (999 units) failing the inventory guard and halting downstream calls.
- **🔵 Customer Order Cancellation**: Explicit customer order cancellation releasing reserved stock.

---

## 2. Microservices Architecture & Cloud Infrastructure

### Control Center
The operational, architectural, and educational command console running on port `8090` that observes, traces, and dispatches requests across all microservices.

### LocalStack Cloud Emulator
The local containerized emulation of AWS cloud services (S3, SQS, SNS, DynamoDB) enabling local-first cloud-native testing without AWS billing.

### Service Registry & Health Probe
The periodic health verification and status aggregation mechanism polling all 8 microservices across their dedicated local ports.

---

## 4. Observability, Resilience & Cloud IaC Domain

### RFC 7807 (ProblemDetail)
The IETF standardized error format implemented via Spring Boot 4 `@RestControllerAdvice` (`org.springframework.http.ProblemDetail`), returning structured JSON error payloads containing `type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `service` metadata.

### OpenTelemetry (OTel) & W3C TraceContext
Industry-standard vendor-neutral telemetry framework. Microservices embed in-process Micrometer Tracing (`micrometer-tracing-bridge-otel`), exporting spans to Grafana Tempo via OTLP (`/v1/traces`) and propagating `traceparent` headers across HTTP requests and Kafka event records.

### Microservice Egress Circuit Breaker (Resilience4j)
Fault tolerance pattern wrapping outbound network integration seams inside microservices. In `order-service`, `@CircuitBreaker` guards synchronous REST pre-flight lookups against `product-service` (failing fast with RFC 7807 503). In `payment-service`, `@CircuitBreaker` wraps the external payment processor gateway to cleanly trigger Saga compensating transactions during gateway outages. Automatically trips to `OPEN` state upon elevated error rates (50%), shedding load without exhausting worker threads.

### Dead Letter Queue (DLQ) & Redrive API
An asynchronous failure isolation mechanism routing poisoned or repeatedly failed messages into a dedicated queue (`order-events-dlq`). The **Redrive API** (`/api/notifications/dlq/redrive/{id}`) enables operators to safely inspect, replay, or purge dead-lettered events without data loss.

### Infrastructure-as-Code (Terraform LocalStack)
Declarative infrastructure provisioning using HashiCorp Terraform modules (`terraform/`) with local AWS endpoint overrides, managing SQS queues, SNS topics, S3 buckets, and DynamoDB tables on LocalStack (`:4566`).

### Umbrella Helm Chart
A unified Kubernetes packaging chart (`helm/ecommerce-platform`) orchestrating deployments, services, configmaps, and ingress across all microservices, with environment-specific overrides (`values-dev.yaml`, `values-prod.yaml`).

### Font Scale
The proportional zoom ratio applied across the dashboard interface, bounded between 80% (0.8x) and 150% (1.5x) to guarantee legible accessibility without breaking layout grids or panel boundaries.

### Scale Stepping
Discrete 10% increments (`+10%` / `-10%`) triggered via toolbar buttons (`A+`, `A-`) or keyboard shortcuts (`Alt + +`, `Alt + -`).

### Scale Indicator
The central button in the header toolbar showing the active scale percentage (e.g. `110%`). Clicking the indicator acts as a one-click reset trigger back to `100%`.

### Scale Persistence
The automated caching of user display scale preferences across page refreshes and browser sessions using browser `localStorage` under the key `cc_font_scale`.

---

## 4. API Gateway, OpenAPI Specification (OAS) & Swagger Architecture

### OpenAPI Specification (OAS 3.0 / 3.1)
The standardized, machine-readable interface definition language (IDL) describing the REST endpoints, operations, request payloads, response structures, and HTTP status codes exposed by each microservice in the cluster.

### Swagger UI Edge Aggregator
The central Swagger UI dashboard running on API Gateway (`http://localhost:8080/swagger-ui.html`) configured with multi-service definition dropdowns. It unifies distributed API specifications into a single interactive console by proxying downstream service `/v3/api-docs`.

### Spec Proxy Seam
The dedicated reverse-proxy route (`/services/{serviceName}/v3/api-docs`) hosted on API Gateway that fetches raw OpenAPI JSON specifications from isolated downstream microservices (Order, Product, Inventory, Payment, Notification) and rewrites target server URLs to Gateway edge paths (`/api/**`).

### Dual-Access Documentation Topology
The dual-surfaced API exploration architecture enabling developers to browse and test APIs either directly on API Gateway (port `8080`) or through the embedded "API Docs / Swagger" explorer tab and service card actions within the Control Center (port `8090`).

### RFC 7807 ProblemDetail Contract
The standardized HTTP error response envelope (`application/problem+json` and `application/json`) conveying machine-parseable error fields (`type`, `title`, `status`, `detail`, `instance`, `timestamp`, `service`, `errors`) across all 4xx/5xx failure responses in the OpenAPI schema definitions.

### Non-Default Auth Toggle
The deployment flag (`-EnableAuth` in `start.ps1` / `scripts/start-local.ps1`) controlling OAuth2 / Keycloak JWT resource server enforcement on API Gateway. It defaults to disabled (all routes open for rapid local iteration) while retaining Swagger UI `Authorize` dialog support via Bearer JWT SecurityScheme definitions.

