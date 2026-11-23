package com.ravi.payment.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Payment Service API",
                version = "v1.0.0",
                description = "Decoupled Payment Microservice featuring deterministic authorization simulations (.99 decline trigger, .50 timeout), strict idempotency guarantees, and PostgreSQL audit logging.",
                contact = @Contact(name = "Payments Team", email = "payments-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "API Gateway Edge Reverse Proxy"),
                @Server(url = "http://localhost:8084", description = "Direct Service Local Port")
        }
)
public class PaymentOpenApiConfig {
}
