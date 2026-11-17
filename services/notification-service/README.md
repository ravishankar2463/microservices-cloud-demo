# Notification Service (Standalone Microservice)

The **Notification Service** is an asynchronous event consumer responsible for processing order notifications, generating receipt documents, and customer communication. It demonstrates:
- **Event-Driven Architecture (EDA)**: AWS SQS message listening using AWS SDK v2.
- **Idempotent Consumer Pattern**: Safe de-duplication of at-least-once SQS messages.
- **Dead Letter Queue (DLQ)**: Routing malformed or unprocessable messages to `order-events-dlq`.
- **AWS S3 Document Archival**: Direct storage of invoices/receipts into `s3://order-invoices-bucket` on the local AWS emulator.

---

## Standalone Quickstart (Clone & Run Alone)

```bash
# 1. Build and test standalone
./mvnw clean test

# 2. Run locally (connects to local AWS emulator at http://localhost:4566)
./mvnw spring-boot:run
```

Or on Windows:
```cmd
mvnw.cmd spring-boot:run
```

### Running with Docker Standalone
```bash
docker build -t notification-service:latest .
docker run -p 8083:8083 \
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 \
  notification-service:latest
```

---

## Health Check Endpoint
```bash
curl http://localhost:8083/actuator/health
```
