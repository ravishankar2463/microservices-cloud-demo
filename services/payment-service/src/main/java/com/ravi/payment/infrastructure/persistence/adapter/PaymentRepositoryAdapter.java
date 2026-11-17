package com.ravi.payment.infrastructure.persistence.adapter;

import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.repository.PaymentRepository;
import com.ravi.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import com.ravi.payment.infrastructure.persistence.repository.PaymentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    private PaymentJpaEntity toEntity(Payment domain) {
        return new PaymentJpaEntity(
            domain.getId(),
            domain.getOrderId(),
            domain.getCustomerId(),
            domain.getAmount(),
            domain.getCurrency(),
            domain.getStatus(),
            domain.getIdempotencyKey(),
            domain.getPaymentMethod(),
            domain.getTransactionReference(),
            domain.getFailureReason(),
            domain.getCreatedAt(),
            domain.getUpdatedAt()
        );
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
            entity.getId(),
            entity.getOrderId(),
            entity.getCustomerId(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getIdempotencyKey(),
            entity.getPaymentMethod(),
            entity.getTransactionReference(),
            entity.getFailureReason(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
