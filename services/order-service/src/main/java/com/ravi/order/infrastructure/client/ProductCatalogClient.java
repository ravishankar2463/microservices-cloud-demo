package com.ravi.order.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);
    private final RestClient restClient;

    public ProductCatalogClient(@Value("${services.product-service.url:http://localhost:8085}") String productServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(productServiceUrl).build();
    }

    public record ProductCatalogItem(
            String id,
            String sku,
            String name,
            BigDecimal price,
            String category,
            int stockQuantity
    ) {}

    @CircuitBreaker(name = "productCatalogService", fallbackMethod = "catalogFallback")
    @Retry(name = "productCatalogService")
    public Optional<ProductCatalogItem> verifyProduct(String productId) {
        log.info("Verifying product [{}] via synchronous REST to product-service", productId);
        try {
            ProductCatalogItem item = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductCatalogItem.class);
            return Optional.ofNullable(item);
        } catch (Exception e) {
            log.warn("Direct lookup for product [{}] failed: {}", productId, e.getMessage());
            throw new ProductCatalogUnavailableException("Failed to verify product with catalog service: " + e.getMessage(), e);
        }
    }

    public Optional<ProductCatalogItem> catalogFallback(String productId, Throwable t) {
        log.warn("Circuit breaker OPEN / Fallback triggered for product [{}] due to: {}", productId, t.getMessage());
        throw new ProductCatalogUnavailableException(
                "Product catalog service is temporarily unavailable. Circuit breaker active for product-service.", t
        );
    }
}
