# Order Service (Standalone Microservice)

The **Order Service** manages the order lifecycle within the e-commerce domain. It is an autonomous, decoupled microservice demonstrating:
- **Hexagonal Architecture (Ports and Adapters)** & Domain-Driven Design (DDD).
- **Transactional Outbox Pattern**: Atomic local transactions with decoupled asynchronous AWS SQS event publishing.
- **Java 21 Features**: Records, Sealed Interfaces, Pattern Matching.
- **Persistence**: Dedicated PostgreSQL database (`order_db`) with Spring Data JPA.
- **AWS Integration**: AWS SQS message publishing using AWS SDK v2, configured for local emulator without sign-in.

---

## Standalone Quickstart (Clone & Run Alone)

You can clone this repository or folder into any standalone directory and run it completely independently of the rest of the system:

```bash
# 1. Build and test standalone
./mvnw clean test

# 2. Run locally (using in-memory H2 fallback and local AWS emulator)
./mvnw spring-boot:run
```

Or on Windows:
```cmd
mvnw.cmd spring-boot:run
```

### Running with Docker Standalone

```bash
docker build -t order-service:latest .
docker run -p 8081:8081 \
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/order_db \
  order-service:latest
```

---

## Health Check Endpoint
```bash
curl http://localhost:8081/actuator/health
```
