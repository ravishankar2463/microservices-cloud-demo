# Product Microservice (`product-service`)

Autonomous catalog microservice managing product aggregates, category categorization, pricing, and active status.

## Architectural Concepts Demonstrated
- **Domain-Driven Design (DDD)**: Product aggregate root, value objects, immutable domain records.
- **Hexagonal Architecture**: Decoupled repository, service, and web layers.
- **Java 21 Features**: Modern records (`ProductDto`, `CreateProductRequest`), sealed interfaces, pattern matching.
- **Database-per-Service**: Isolated `product_db` PostgreSQL schema with zero cross-service foreign keys.
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
| `GET` | `/api/products` | Query products (supports optional `category` and `search` filters) |
| `GET` | `/api/products/{id}` | Retrieve product by primary ID |
| `GET` | `/api/products/sku/{sku}` | Retrieve product by unique SKU |
| `POST` | `/api/products` | Create a new product aggregate |
| `DELETE` | `/api/products/{id}` | Soft-deactivate a product |
