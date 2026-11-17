package com.ravi.controlcenter.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SagaExecution(
    String sagaId,
    String orderId,
    String customerId,
    BigDecimal amount,
    String status,        // PENDING, CONFIRMED, COMPENSATED, FAILED
    String currentStep,
    List<SagaStep> steps,
    boolean compensated,
    Instant startedAt,
    Instant completedAt
) {
    public record SagaStep(
        String stepName,
        String service,
        String status,      // COMPLETED, FAILED, COMPENSATING, COMPENSATED, PENDING
        String details,
        long durationMs,
        InfrastructureSnapshot beforeState,
        InfrastructureSnapshot afterState,
        InfrastructureDiff impactDiff,
        AopDebugTrace aopTrace,
        ArchitecturalDossier dossier
    ) {
        public SagaStep(String stepName, String service, String status, String details, long durationMs) {
            this(stepName, service, status, details, durationMs, null, null, null, null, null);
        }
    }
}
