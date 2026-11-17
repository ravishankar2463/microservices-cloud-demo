package com.ravi.common.domain.model;

import com.ravi.common.domain.event.OrderCancelledEvent;
import com.ravi.common.domain.event.OrderCreatedEvent;
import com.ravi.common.domain.event.OrderEvent;
import com.ravi.common.domain.event.OrderPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainFeaturesTest {

    @Test
    @DisplayName("Result monad success mapping and error handling")
    void testResultSuccess() {
        Result<String, String> initial = Result.success("ORD-101");
        Result<String, String> mapped = initial.map(String::toLowerCase);

        assertThat(mapped.isSuccess()).isTrue();
        assertThat(mapped.getSuccess()).contains("ord-101");
        assertThat(mapped.getFailure()).isEmpty();
    }

    @Test
    @DisplayName("Result monad failure mapping")
    void testResultFailure() {
        Result<String, Integer> initial = Result.failure(404);
        Result<String, String> mapped = initial.mapError(code -> "Error code: " + code);

        assertThat(mapped.isFailure()).isTrue();
        assertThat(mapped.getFailure()).contains("Error code: 404");
    }

    @Test
    @DisplayName("Pattern matching over sealed domain events")
    void testPatternMatchingEvents() {
        OrderEvent created = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CUST-001",
                List.of(new OrderCreatedEvent.OrderItemPayload("PROD-1", 2, BigDecimal.valueOf(49.99))),
                BigDecimal.valueOf(99.98),
                Instant.now()
        );

        String description = switch (created) {
            case OrderCreatedEvent oce -> "Order created for: " + oce.customerId();
            case OrderPaidEvent ope -> "Order paid: " + ope.paymentReference();
            case OrderCancelledEvent oce -> "Order cancelled: " + oce.reason();
        };

        assertThat(description).isEqualTo("Order created for: CUST-001");
        assertThat(created.eventType()).isEqualTo("ORDER_CREATED");
    }
}
