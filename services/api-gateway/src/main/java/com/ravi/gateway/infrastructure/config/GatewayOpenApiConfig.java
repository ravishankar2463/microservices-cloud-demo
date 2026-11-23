package com.ravi.gateway.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API Gateway Edge API & Cluster Aggregator",
                version = "v1.0.0",
                description = "Central API Gateway Layer showcasing Edge Reverse Proxying, Token-Bucket Rate Limiting, X-Correlation-ID Tracking, OAuth2 JWT Validation, and Unified Swagger UI Aggregation across all microservices.",
                contact = @Contact(name = "Edge & API Gateway Team", email = "gateway-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local API Gateway Edge Host (:8080)")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Keycloak OAuth2 JWT Access Token. When security is enabled, click 'Authorize' and enter your Bearer token."
)
public class GatewayOpenApiConfig {
}
