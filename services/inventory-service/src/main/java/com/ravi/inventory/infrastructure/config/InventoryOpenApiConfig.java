package com.ravi.inventory.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Inventory Service API",
                version = "v1.0.0",
                description = "Decoupled Inventory Microservice featuring Redis distributed locks (SET NX PX), Java 21 Virtual Threads, and Reserve -> Commit / Release lifecycle management.",
                contact = @Contact(name = "Inventory Engineering Team", email = "inventory-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "API Gateway Edge Reverse Proxy"),
                @Server(url = "http://localhost:8082", description = "Direct Service Local Port")
        }
)
public class InventoryOpenApiConfig {
}
