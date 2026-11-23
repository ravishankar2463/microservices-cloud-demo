package com.ravi.order.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Order Service API",
                version = "v1.0.0",
                description = "Decoupled Order Microservice showcasing Hexagonal Architecture, CQRS (Write/Read models), Transactional Outbox pattern, and Kafka/SQS event streaming.",
                contact = @Contact(name = "Platform Engineering Team", email = "platform-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "API Gateway Edge Reverse Proxy"),
                @Server(url = "http://localhost:8081", description = "Direct Service Local Port")
        }
)
public class OrderOpenApiConfig {
}
