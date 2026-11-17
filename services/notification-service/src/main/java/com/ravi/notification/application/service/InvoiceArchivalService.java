package com.ravi.notification.application.service;

import com.ravi.notification.domain.model.Invoice;
import com.ravi.notification.domain.repository.InvoiceArchiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InvoiceArchivalService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceArchivalService.class);

    private final InvoiceArchiveRepository invoiceRepository;

    public InvoiceArchivalService(InvoiceArchiveRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public boolean archiveInvoice(String orderId, String invoicePayload) {
        Invoice invoice = Invoice.create(orderId, orderId, invoicePayload);
        invoiceRepository.save(invoice);
        log.info("Archived invoice domain model for order: {}", orderId);
        return true;
    }

    public Optional<Invoice> getInvoice(String orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }
}
