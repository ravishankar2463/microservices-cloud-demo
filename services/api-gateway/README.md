# API Gateway (Standalone Edge Service)

The **API Gateway** acts as the single entry point for all client requests entering the microservices ecosystem. It demonstrates:
- **Edge Routing & Reverse Proxying**: Routes traffic to `order-service` and `inventory-service`.
- **Distributed Tracing & Correlation ID**: Generates and propagates `X-Correlation-ID` across all downstream requests.
- **Token Bucket Rate Limiting**: Protects backend services from traffic bursts.
- **Unified Health & Actuator Aggregation**: Monitors the operational status of the cluster.

---

## Standalone Quickstart (Clone & Run Alone)

```bash
# 1. Build and test standalone
./mvnw clean test

# 2. Run locally (port 8080)
./mvnw spring-boot:run
```

Or on Windows:
```cmd
mvnw.cmd spring-boot:run
```

### Running with Docker Standalone
```bash
docker build -t api-gateway:latest .
docker run -p 8080:8080 api-gateway:latest
```

---

## Health Check Endpoint
```bash
curl http://localhost:8080/actuator/health
```
