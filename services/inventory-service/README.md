# Inventory Service (Standalone Microservice)

The **Inventory Service** manages stock quantities, reservations, and inventory health. It is an autonomous, decoupled microservice demonstrating:
- **Java 21 Virtual Threads (Project Loom)**: Non-blocking high concurrency for flash sales and rapid order processing (`spring.threads.virtual.enabled: true`).
- **Redis Cache-Aside**: High-speed stock lookups and distributed locking to eliminate race conditions.
- **AWS DynamoDB Local**: NoSQL persistence layer for item catalogs using AWS SDK v2 with zero cloud credentials needed.
- **Resilience4j**: Circuit breakers, bulkheads, and retries.

---

## Standalone Quickstart (Clone & Run Alone)

```bash
# 1. Build and test standalone
./mvnw clean test

# 2. Run locally (requires Redis on port 6379, or run with fallback)
./mvnw spring-boot:run
```

Or on Windows:
```cmd
mvnw.cmd spring-boot:run
```

### Running with Docker Standalone
```bash
docker build -t inventory-service:latest .
docker run -p 8082:8082 \
  -e REDIS_HOST=host.docker.internal \
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 \
  inventory-service:latest
```

---

## Health Check Endpoint
```bash
curl http://localhost:8082/actuator/health
```
