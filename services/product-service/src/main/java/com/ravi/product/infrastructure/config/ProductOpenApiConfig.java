package com.ravi.product.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Product Service API",
                version = "v1.0.0",
                description = "Decoupled Product Microservice showcasing Catalog Aggregates, Category Projections, Flyway schema migrations, and Java 21 Records.",
                contact = @Contact(name = "Catalog Engineering Team", email = "catalog-dev@ecommerce.local")
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "API Gateway Edge Reverse Proxy"),
                @Server(url = "http://localhost:8085", description = "Direct Service Local Port")
        }
)
public class ProductOpenApiConfig {
}
