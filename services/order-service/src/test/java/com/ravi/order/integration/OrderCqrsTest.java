package com.ravi.order.integration;

import com.ravi.order.application.command.CancelOrderCommand;
import com.ravi.order.application.command.CreateOrderCommand;
import com.ravi.order.application.command.handler.OrderCommandHandler;
import com.ravi.order.application.query.GetOrderByIdQuery;
import com.ravi.order.application.query.handler.OrderQueryHandler;
import com.ravi.order.application.query.model.OrderView;
import com.ravi.order.domain.model.OrderStatus;
import com.ravi.order.infrastructure.messaging.outbox.OutboxJpaEntity;
import com.ravi.order.infrastructure.messaging.outbox.OutboxJpaRepository;
import com.ravi.order.infrastructure.persistence.entity.OrderJpaEntity;
import com.ravi.order.infrastructure.persistence.repository.OrderJpaRepository;
import com.ravi.order.saga.orchestration.OrderSagaOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderCqrsTest {

    @Autowired
    private OrderCommandHandler commandHandler;

    @Autowired
    private OrderQueryHandler queryHandler;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Test
    @DisplayName("CQRS: CreateOrderCommand stores aggregate in write-db and queues event in Outbox")
    void testCreateOrderAndOutboxPattern() {
        CreateOrderCommand command = new CreateOrderCommand(
                "CUST-100",
                List.of(
                        new CreateOrderCommand.ItemCommand("PROD-LAPTOP", 1, BigDecimal.valueOf(1200.00)),
                        new CreateOrderCommand.ItemCommand("PROD-MOUSE", 2, BigDecimal.valueOf(25.00))
                )
        );

        // 1. Execute Command
        UUID orderId = commandHandler.handle(command);
        assertThat(orderId).isNotNull();

        // 2. Verify Write Model (Postgres/H2)
        Optional<OrderJpaEntity> entityOpt = orderRepository.findById(orderId);
        assertThat(entityOpt).isPresent();
        OrderJpaEntity entity = entityOpt.get();
        assertThat(entity.getCustomerId()).isEqualTo("CUST-100");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1250.00));
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(entity.getItems()).hasSize(2);

        // 3. Verify Transactional Outbox Event
        List<OutboxJpaEntity> outboxEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outboxEvents).anyMatch(e -> e.getAggregateId().equals(orderId.toString()) && e.getEventType().equals("ORDER_CREATED"));

        // 4. Verify CQRS Query Model
        Optional<OrderView> queryView = queryHandler.handle(new GetOrderByIdQuery(orderId));
        assertThat(queryView).isPresent();
        assertThat(queryView.get().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1250.00));
        assertThat(queryView.get().status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CQRS: CancelOrderCommand updates order status and logs cancellation outbox event")
    void testCancelOrderCommand() {
        CreateOrderCommand createCommand = new CreateOrderCommand(
                "CUST-200",
                List.of(new CreateOrderCommand.ItemCommand("PROD-KEYBOARD", 1, BigDecimal.valueOf(80.00)))
        );
        UUID orderId = commandHandler.handle(createCommand);

        // Execute Cancel Command
        commandHandler.handle(new CancelOrderCommand(orderId, "Customer found a better price"));

        // Verify Status in Read Query
        Optional<OrderView> queryView = queryHandler.handle(new GetOrderByIdQuery(orderId));
        assertThat(queryView).isPresent();
        assertThat(queryView.get().status()).isEqualTo("CANCELLED");

        // Verify Outbox has ORDER_CANCELLED event
        List<OutboxJpaEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).anyMatch(e -> e.getAggregateId().equals(orderId.toString()) && e.getEventType().equals("ORDER_CANCELLED"));
    }

    @Test
    @DisplayName("Saga: Confirm order marks order as CONFIRMED")
    void testConfirmOrder() {
        CreateOrderCommand createCommand = new CreateOrderCommand(
                "CUST-300",
                List.of(new CreateOrderCommand.ItemCommand("PROD-MONITOR", 1, BigDecimal.valueOf(300.00)))
        );
        UUID orderId = commandHandler.handle(createCommand);

        commandHandler.confirmOrder(orderId);

        Optional<OrderView> queryView = queryHandler.handle(new GetOrderByIdQuery(orderId));
        assertThat(queryView).isPresent();
        assertThat(queryView.get().status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Saga Orchestrator: Payment failure payload triggers compensating cancellation")
    void testSagaPaymentFailureTriggersCompensation() {
        CreateOrderCommand createCommand = new CreateOrderCommand(
                "CUST-400",
                List.of(new CreateOrderCommand.ItemCommand("PROD-PHONE", 1, BigDecimal.valueOf(999.00)))
        );
        UUID orderId = commandHandler.handle(createCommand);

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(
                commandHandler,
                new tools.jackson.databind.ObjectMapper()
        );

        // Simulate receiving PaymentFailedEvent JSON payload
        String paymentFailedPayload = """
            {
                "orderId": "%s",
                "customerId": "CUST-400",
                "amount": 999.00,
                "failureReason": "Card declined: Insufficient funds (Simulated trigger .99)"
            }
        """.formatted(orderId);

        orchestrator.onPaymentEvent(paymentFailedPayload);

        // Verify order is cancelled and outbox compensation event is stored
        Optional<OrderView> queryView = queryHandler.handle(new GetOrderByIdQuery(orderId));
        assertThat(queryView).isPresent();
        assertThat(queryView.get().status()).isEqualTo("CANCELLED");

        List<OutboxJpaEntity> outbox = outboxRepository.findAll();
        assertThat(outbox).anyMatch(e -> e.getAggregateId().equals(orderId.toString())
                && e.getEventType().equals("ORDER_CANCELLED")
                && e.getPayload().contains("Payment failed"));
    }
}
