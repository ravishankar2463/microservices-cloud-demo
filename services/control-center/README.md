# Local Microservices Control Center & Dashboard

Autonomous architectural visualization, operational telemetry aggregator, and interactive developer console for the Java Microservices Platform.

## 🚀 Architectural Role

- **Port**: `8090`
- **Frontend UI**: Integrated modern dark-first console available at `http://localhost:8090`
- **Backend API**:
  - `GET /api/admin/overview`: System overview, KPIs, and operational health
  - `GET /api/admin/services`: Live discovery and port registry of all ecosystem microservices
  - `GET /api/admin/architecture`: Graph topology (nodes, edges, communication types, database ownership)
  - `GET /api/admin/events`: Masked Kafka event stream explorer (`order-events`, `payment-events`, `inventory-events`)
  - `GET /api/admin/sagas`: Distributed transaction & Saga state timeline explorer
  - `GET /api/admin/learning`: Interactive microservices architecture pattern guides with code anchors
  - `POST /api/admin/operations/demo-workflow`: Predefined demonstration scenarios runner (Happy Path, Payment Decline, Inventory Unavailable, Gateway Timeout)

## 🛠️ Technology Stack

- **Java 21**: Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) for non-blocking concurrent health probes
- **Spring Boot 4.1.2**: Web MVC, Actuator
- **Modern ES6 / SVG Console**: Zero external npm dependencies required at runtime

## 🏃 Running Standalone

```bash
# Compile and run
./mvnw spring-boot:run

# Open developer console
open http://localhost:8090
```
