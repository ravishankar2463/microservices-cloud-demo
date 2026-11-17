package com.ravi.notification;

import com.ravi.notification.application.service.InvoiceArchivalService;
import com.ravi.notification.domain.model.Invoice;
import com.ravi.notification.infrastructure.messaging.inbox.IdempotentMessageStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationServiceTests {

    @Autowired
    private IdempotentMessageStore idempotencyStore;

    @Autowired
    private InvoiceArchivalService invoiceService;

    @Test
    @DisplayName("Idempotency: First event passes, duplicate event is identified and rejected")
    void testIdempotentConsumer() {
        String eventId = UUID.randomUUID().toString();

        boolean firstAttempt = idempotencyStore.processIfNotSeen(eventId);
        assertThat(firstAttempt).isTrue();

        // Second attempt with exact same event ID must return false (duplicate)
        boolean secondAttempt = idempotencyStore.processIfNotSeen(eventId);
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("Invoice Archival: Generates invoice and archives into S3 / store")
    void testInvoiceArchival() {
        String orderId = UUID.randomUUID().toString();
        String payload = "{\"orderId\":\"" + orderId + "\",\"amount\":150.00}";

        boolean archived = invoiceService.archiveInvoice(orderId, payload);
        assertThat(archived).isTrue();

        Optional<Invoice> invoice = invoiceService.getInvoice(orderId);
        assertThat(invoice).isPresent();
        assertThat(invoice.get().payload()).contains(orderId);
    }
}
