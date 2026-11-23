package com.ravi.gateway.presentation.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Edge Reverse Proxy for aggregating downstream OpenAPI Specifications into the central Swagger UI.
 */
@RestController
@Tag(name = "OpenAPI Aggregator", description = "Reverse proxy endpoints for aggregating microservice OpenAPI specifications")
public class GatewaySwaggerAggregationController {

    private static final Logger log = LoggerFactory.getLogger(GatewaySwaggerAggregationController.class);

    private final RestClient restClient;

    @Value("${order.service.url:http://localhost:8081}")
    private String orderServiceUrl = "http://localhost:8081";

    @Value("${inventory.service.url:http://localhost:8082}")
    private String inventoryServiceUrl = "http://localhost:8082";

    @Value("${product.service.url:http://localhost:8085}")
    private String productServiceUrl = "http://localhost:8085";

    @Value("${payment.service.url:http://localhost:8084}")
    private String paymentServiceUrl = "http://localhost:8084";

    @Value("${notification.service.url:http://localhost:8083}")
    private String notificationServiceUrl = "http://localhost:8083";

    public GatewaySwaggerAggregationController() {
        this(RestClient.builder().build());
    }

    public GatewaySwaggerAggregationController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping(value = {"/services/{serviceName}/openapi.json", "/services/{serviceName}/openapi"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Fetch downstream OpenAPI JSON", description = "Proxies the /v3/api-docs endpoint of the requested microservice for central Swagger UI aggregation.")
    public ResponseEntity<String> getServiceApiDocs(
            @Parameter(description = "Service identifier (order-service, product-service, inventory-service, payment-service, notification-service)", example = "order-service")
            @PathVariable String serviceName) {

        String targetBaseUrl = switch (serviceName.toLowerCase()) {
            case "order-service" -> orderServiceUrl;
            case "inventory-service" -> inventoryServiceUrl;
            case "product-service" -> productServiceUrl;
            case "payment-service" -> paymentServiceUrl;
            case "notification-service" -> notificationServiceUrl;
            default -> null;
        };

        if (targetBaseUrl == null) {
            log.warn("Unknown service requested for OpenAPI docs: {}", serviceName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Unknown service name: " + serviceName + "\"}");
        }

        try {
            String spec = restClient.get()
                    .uri(targetBaseUrl + "/v3/api-docs")
                    .retrieve()
                    .body(String.class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(spec);
        } catch (Exception e) {
            log.error("Failed to fetch OpenAPI docs from {} ({}): {}", serviceName, targetBaseUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Unable to reach " + serviceName + " at " + targetBaseUrl + ": " + e.getMessage() + "\"}");
        }
    }
}
