package com.ravi.order.integration;

import com.ravi.order.infrastructure.client.ProductCatalogClient;
import com.ravi.order.infrastructure.client.ProductCatalogUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProductCatalogCircuitBreakerTest {

    @Autowired
    private ProductCatalogClient productCatalogClient;

    @Test
    @DisplayName("Circuit Breaker: ProductCatalogClient falls back with ProductCatalogUnavailableException when product-service is unreachable")
    void shouldTriggerCircuitBreakerFallbackWhenProductServiceUnreachable() {
        // When calling an unstarted product service endpoint (localhost:8085), Resilience4j retries and executes catalogFallback
        assertThatThrownBy(() -> productCatalogClient.verifyProduct("NON-EXISTENT-PROD"))
                .isInstanceOf(ProductCatalogUnavailableException.class)
                .hasMessageContaining("Product catalog service is temporarily unavailable");
    }
}
