package com.ravi.gateway.presentation.rest.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GatewayHomeController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> homeHtml(@RequestHeader(value = "Accept", defaultValue = "") String acceptHeader) {
        if (!acceptHeader.isEmpty() && !acceptHeader.contains("text/html") && acceptHeader.contains("application/json")) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildJsonPayload());
        }

        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>API Gateway | Local Microservices Edge (:8080)</title>
              <style>
                :root {
                  --bg-dark: #0f172a;
                  --card-bg: #1e293b;
                  --border-color: #334155;
                  --text-main: #f8fafc;
                  --text-muted: #94a3b8;
                  --accent-cyan: #06b6d4;
                  --accent-blue: #3b82f6;
                  --accent-green: #10b981;
                  --accent-amber: #f59e0b;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                  background: var(--bg-dark);
                  color: var(--text-main);
                  min-height: 100vh;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  padding: 24px;
                }
                .container {
                  max-width: 860px;
                  width: 100%;
                  background: var(--card-bg);
                  border: 1px solid var(--border-color);
                  border-radius: 12px;
                  box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5);
                  padding: 36px;
                }
                .header {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  border-bottom: 1px solid var(--border-color);
                  padding-bottom: 20px;
                  margin-bottom: 24px;
                }
                .title-group h1 {
                  font-size: 24px;
                  font-weight: 700;
                  color: var(--text-main);
                  display: flex;
                  align-items: center;
                  gap: 10px;
                }
                .badge-live {
                  background: rgba(16, 185, 129, 0.2);
                  color: var(--accent-green);
                  border: 1px solid rgba(16, 185, 129, 0.4);
                  font-size: 11px;
                  font-weight: 700;
                  padding: 4px 10px;
                  border-radius: 9999px;
                  text-transform: uppercase;
                  letter-spacing: 0.05em;
                }
                .banner {
                  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(6, 182, 212, 0.15));
                  border: 1px solid rgba(59, 130, 246, 0.4);
                  border-radius: 8px;
                  padding: 18px 24px;
                  margin-bottom: 28px;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                }
                .banner-text h3 {
                  font-size: 16px;
                  font-weight: 600;
                  color: #93c5fd;
                  margin-bottom: 4px;
                }
                .banner-text p {
                  font-size: 13px;
                  color: var(--text-muted);
                }
                .btn {
                  display: inline-flex;
                  align-items: center;
                  gap: 8px;
                  background: var(--accent-blue);
                  color: #fff;
                  text-decoration: none;
                  font-size: 13px;
                  font-weight: 600;
                  padding: 10px 18px;
                  border-radius: 6px;
                  transition: background 0.15s ease, transform 0.1s ease;
                  white-space: nowrap;
                }
                .btn:hover { background: #2563eb; transform: translateY(-1px); }
                .section-title {
                  font-size: 14px;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: 0.05em;
                  color: var(--text-muted);
                  margin-bottom: 12px;
                }
                .grid {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 16px;
                  margin-bottom: 24px;
                }
                .card {
                  background: rgba(15, 23, 42, 0.6);
                  border: 1px solid var(--border-color);
                  border-radius: 8px;
                  padding: 16px;
                }
                .card h4 {
                  font-size: 14px;
                  font-weight: 600;
                  color: var(--accent-cyan);
                  margin-bottom: 8px;
                }
                .card p {
                  font-size: 12px;
                  color: var(--text-muted);
                  line-height: 1.5;
                  margin-bottom: 10px;
                }
                .endpoint-tag {
                  font-family: monospace;
                  font-size: 12px;
                  background: rgba(0,0,0,0.3);
                  padding: 4px 8px;
                  border-radius: 4px;
                  color: #cbd5e1;
                  display: inline-block;
                  text-decoration: none;
                }
                .endpoint-tag:hover {
                  color: var(--accent-cyan);
                }
                .code-box {
                  background: #020617;
                  border: 1px solid var(--border-color);
                  border-radius: 6px;
                  padding: 12px;
                  font-family: monospace;
                  font-size: 11px;
                  color: #e2e8f0;
                  overflow-x: auto;
                  line-height: 1.4;
                }
                .footer {
                  margin-top: 24px;
                  padding-top: 16px;
                  border-top: 1px solid var(--border-color);
                  font-size: 12px;
                  color: var(--text-muted);
                  text-align: center;
                }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <div class="title-group">
                    <h1>⚡ API Gateway Edge</h1>
                    <span style="font-size: 13px; color: var(--text-muted);">Port :8080 &bull; Reverse Proxy &bull; JWT Validation &bull; Rate Limiting</span>
                  </div>
                  <span class="badge-live">● Operational</span>
                </div>

                <div class="banner">
                  <div class="banner-text">
                    <h3>Interactive OpenAPI Documentation & Cluster Control Center</h3>
                    <p>Explore unified OpenAPI specifications across all microservices or launch the Operations Dashboard.</p>
                  </div>
                  <div style="display: flex; gap: 10px; flex-wrap: wrap;">
                    <a href="/swagger-ui.html" class="btn" style="background: var(--accent-cyan); color: #0f172a;">📖 Swagger UI</a>
                    <a href="http://localhost:8090" class="btn">🚀 Control Center (:8090)</a>
                  </div>
                </div>

                <div class="section-title">Public Endpoints (No Authentication Required)</div>
                <div class="grid">
                  <div class="card">
                    <h4>Interactive Swagger UI</h4>
                    <p>Explore & execute live API requests with the multi-service OAS dropdown aggregator.</p>
                    <a href="/swagger-ui.html" class="endpoint-tag" style="color: var(--accent-cyan);">GET /swagger-ui.html</a>
                  </div>
                  <div class="card">
                    <h4>Downstream Routes Registry</h4>
                    <p>Returns JSON mappings for all downstream microservice target URLs.</p>
                    <a href="/api/routes" class="endpoint-tag">GET /api/routes</a>
                  </div>
                  <div class="card">
                    <h4>Product Catalog</h4>
                    <p>Public access to aggregate product catalog items and categories.</p>
                    <a href="/api/products" class="endpoint-tag">GET /api/products</a>
                  </div>
                  <div class="card">
                    <h4>Health Actuator</h4>
                    <p>Liveness and readiness probes for Kubernetes and container health.</p>
                    <a href="/actuator/health" class="endpoint-tag">GET /actuator/health</a>
                  </div>
                  <div class="card">
                    <h4>Circuit Breaker Fallback</h4>
                    <p>Downstream error fallback endpoint triggered on timeouts.</p>
                    <a href="/api/fallback" class="endpoint-tag">GET /api/fallback</a>
                  </div>
                </div>

                <div class="section-title">Protected Endpoints (OAuth2 / JWT Required)</div>
                <div class="grid">
                  <div class="card">
                    <h4>Orders Service (Saga CQRS)</h4>
                    <p>Requires Bearer token with <code>ROLE_CUSTOMER</code> or <code>ROLE_ADMIN</code>.</p>
                    <span class="endpoint-tag">POST /api/orders &bull; GET /api/orders</span>
                  </div>
                  <div class="card">
                    <h4>Token Identity Inspection</h4>
                    <p>Extracts decoded username and granted realm roles from JWT.</p>
                    <span class="endpoint-tag">GET /api/auth/me</span>
                  </div>
                </div>

                <div class="section-title">How to obtain a Keycloak Bearer Token</div>
                <div class="code-box">
curl -X POST http://localhost:8088/realms/ecommerce/protocol/openid-connect/token \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -d "client_id=gateway-client" \\
  -d "username=customer1" \\
  -d "password=password" \\
  -d "grant_type=password"
                </div>

                <div class="footer">
                  Microservices Architecture Platform &bull; Antigravity Demo &bull; 
                  <a href="http://localhost:8090" style="color: var(--accent-cyan); text-decoration: none;">Control Center Dashboard</a>
                </div>
              </div>
            </body>
            </html>
            """;

        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> homeJson() {
        return ResponseEntity.ok(Map.of(
            "service", "api-gateway",
            "port", 8080,
            "status", "UP",
            "swaggerUrl", "http://localhost:8080/swagger-ui.html",
            "dashboardUrl", "http://localhost:8090",
            "routesUrl", "http://localhost:8080/api/routes",
            "productsUrl", "http://localhost:8080/api/products",
            "healthUrl", "http://localhost:8080/actuator/health",
            "keycloakUrl", "http://localhost:8088",
            "message", "Welcome to API Gateway Edge. Visit /swagger-ui.html for interactive API documentation or http://localhost:8090 for the Control Center Dashboard."
        ));
    }

    private String buildJsonPayload() {
        return """
            {
              "service": "api-gateway",
              "port": 8080,
              "status": "UP",
              "swaggerUrl": "http://localhost:8080/swagger-ui.html",
              "dashboardUrl": "http://localhost:8090",
              "routesUrl": "http://localhost:8080/api/routes",
              "productsUrl": "http://localhost:8080/api/products",
              "healthUrl": "http://localhost:8080/actuator/health",
              "keycloakUrl": "http://localhost:8088",
              "message": "Welcome to API Gateway Edge. Visit /swagger-ui.html for interactive API documentation or http://localhost:8090 for the Control Center Dashboard."
            }
            """;
    }
}
