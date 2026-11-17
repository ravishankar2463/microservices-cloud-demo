# Payment Microservice (`payment-service`)

Autonomous payment microservice managing transaction authorization, capture, idempotency, and deterministic payment gateway simulations.

## Architectural Concepts Demonstrated
- **Simulated Payment Provider Adapter**: Deterministic payment scenarios for testing Saga workflows:
  - Amounts ending in `.99` (e.g. `$19.99`): Triggers **DECLINED (Insufficient Funds)** for Saga compensation testing.
  - Amounts ending in `.50` (e.g. `$49.50`): Triggers **FAILED (Gateway Timeout)** for resilience testing.
  - Standard amounts: Triggers **CAPTURED** with a unique transaction reference.
- **Idempotency Guarantee**: Strict deduplication on `idempotency_key` ensuring zero double-charging.
- **Event-Driven Messaging**: Emits `PaymentCapturedEvent` and `PaymentFailedEvent` to Kafka topic `payment-events`.
- **Database-per-Service**: Dedicated `payment_db` PostgreSQL schema.
- **Standalone Polyrepo Entity**: Dedicated Maven wrapper (`mvnw`), independent Git history, containerized Dockerfile.

## Quick Start (Standalone Mode)

```bash
# Build and test
./mvnw clean test

# Run standalone with local DB
./mvnw spring-boot:run
```

## REST Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/payments/process` | Process or idempotently retrieve payment |
| `GET` | `/api/payments/order/{orderId}` | Retrieve payment record by Order ID |
| `GET` | `/api/payments/{id}` | Retrieve payment record by primary ID |
