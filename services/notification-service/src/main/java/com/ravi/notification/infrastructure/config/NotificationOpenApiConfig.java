package com.ravi.notification.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Notification Service API",
                version = "v1.0.0",
                description = "Decoupled Notification Microservice showcasing idempotent message consumption, Dead Letter Queue (DLQ) replay/redrive, and LocalStack S3 PDF invoice archiving.",
                contact = @Contact(name = "Notifications Team", email = "notifications-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "API Gateway Edge Reverse Proxy"),
                @Server(url = "http://localhost:8083", description = "Direct Service Local Port")
        }
)
public class NotificationOpenApiConfig {
}
