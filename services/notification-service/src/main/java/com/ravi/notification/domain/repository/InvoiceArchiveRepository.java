package com.ravi.notification.domain.repository;

import com.ravi.notification.domain.model.Invoice;

import java.util.Optional;

public interface InvoiceArchiveRepository {
    void save(Invoice invoice);
    Optional<Invoice> findByOrderId(String orderId);
    int clearAll();
}
