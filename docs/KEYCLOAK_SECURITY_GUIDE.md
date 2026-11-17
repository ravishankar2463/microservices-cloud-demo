# Centralized Security Architecture with Keycloak (OAuth2 & OIDC)

This document describes the centralized identity and access management (IAM) architecture for the **E-Commerce Microservices Platform**, implemented using **Keycloak 25**, **Spring Security 7.x**, and **OAuth2 Resource Server** with JSON Web Tokens (JWT).

---

## 1. Security Architecture Topology

```
                                    ┌────────────────────────────────────────────────────────┐
                                    │                     Client Request                     │
                                    │        (Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6...)        │
                                    └───────────────────────────┬────────────────────────────┘
                                                                │
                                                                ▼
┌───────────────────────────────────────┐           ┌────────────────────────────────────────┐
│             Keycloak 25               │           │          API Gateway (:8080)           │
│        Realm: "ecommerce"             │◄──────────┤ - Validates JWT via JWKS Certs Endpoint│
│   (Port 8088 -> Container 8080)       │ JWKS Certs│ - Extracts "realm_access.roles"        │
│                                       │           │ - Enforces RBAC Matrix                 │
│ Users:                                │           │ - Injects X-User-Id, X-User-Roles      │
│ - customer1 (ROLE_CUSTOMER)           │           └───────────────────┬────────────────────┘
│ - admin1    (ROLE_ADMIN)              │                               │
└───────────────────────────────────────┘                               │ Propagates headers
                                                                        ▼ (X-User-Id, X-User-Roles)
                                                    ┌────────────────────────────────────────┐
                                                    │          Downstream Services           │
                                                    │   (Order, Inventory, Product, etc.)    │
                                                    └────────────────────────────────────────┘
```

---

## 2. Pre-Seeded Realm & Credentials

The platform ships with a pre-configured realm import file: [docker/keycloak/ecommerce-realm.json](file:///c:/Users/ravis/Documents/Workspace/aws-demo/docker/keycloak/ecommerce-realm.json).

| Entity | Name / Value | Description |
| :--- | :--- | :--- |
| **Realm** | `ecommerce` | Dedicated isolated realm for all e-commerce microservices |
| **Keycloak Admin** | `admin` / `admin` | Superuser for Keycloak Admin Console at `http://localhost:8088/admin` |
| **Customer User** | `customer1` / `password123` | Assigned role `ROLE_CUSTOMER` |
| **Admin User** | `admin1` / `password123` | Assigned roles `ROLE_ADMIN` and `ROLE_CUSTOMER` |
| **Gateway Client** | `ecommerce-gateway` | Public OIDC client with direct access grants enabled |
| **Services Client** | `ecommerce-services` | Bearer-only resource server client |

---

## 3. Role-Based Access Control (RBAC) Matrix

| Endpoint | Method | Required Authority | Description |
| :--- | :--- | :--- | :--- |
| `/api/routes` | `GET` | Public (`permitAll()`) | Gateway routing directory |
| `/api/products` | `GET` | Public (`permitAll()`) | Public product catalog browsing |
| `/api/products/{id}` | `GET` | Public (`permitAll()`) | View product details |
| `/actuator/**` | `GET` | Public (`permitAll()`) | Health, metrics, and readiness checks |
| `/api/orders/**` | `POST` | `ROLE_CUSTOMER` or `ROLE_ADMIN` | Order creation and checkout workflows |
| `/api/orders/**` | `GET` | `ROLE_CUSTOMER` or `ROLE_ADMIN` | Query order status and history |
| `/api/products` | `POST` | `ROLE_ADMIN` | Add new catalog items |
| `/api/products/**` | `DELETE` | `ROLE_ADMIN` | Deactivate/remove catalog items |

---

## 4. How to Obtain a JWT Token

You can request a JWT access token directly from Keycloak via the OAuth2 Password Grant:

```bash
curl -X POST http://localhost:8088/realms/ecommerce/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ecommerce-gateway" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password123"
```

### Sample JWT Payload Decoded:
```json
{
  "exp": 1731845000,
  "iat": 1731844700,
  "iss": "http://localhost:8088/realms/ecommerce",
  "sub": "b2c1f9e2-8231-419b-a78d-128374902183",
  "preferred_username": "customer1",
  "email": "customer1@example.com",
  "realm_access": {
    "roles": [
      "ROLE_CUSTOMER",
      "default-roles-ecommerce"
    ]
  }
}
```

---

## 5. Downstream Identity Propagation

When a client presents a valid Bearer token, the API Gateway:
1. Validates the signature using Keycloak's JWKS public keys.
2. Formats Keycloak roles using [KeycloakJwtAuthenticationConverter.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/api-gateway/src/main/java/com/ravi/gateway/security/KeycloakJwtAuthenticationConverter.java).
3. Injects identity headers into downstream REST requests in [GatewayRoutingController.java](file:///c:/Users/ravis/Documents/Workspace/aws-demo/services/api-gateway/src/main/java/com/ravi/gateway/controller/GatewayRoutingController.java):
   - `X-User-Id`: User subject ID (`sub`)
   - `X-User-Name`: Authenticated username (`customer1`)
   - `X-User-Roles`: Comma-separated granted authorities (`ROLE_CUSTOMER`)
   - `X-Correlation-ID`: Trace correlation GUID
