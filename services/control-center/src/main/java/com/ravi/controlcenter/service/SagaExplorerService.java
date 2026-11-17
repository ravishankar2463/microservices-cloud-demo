package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.SagaExecution;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SagaExplorerService {

    private final Map<String, SagaExecution> sagaStore = new ConcurrentHashMap<>();

    public SagaExplorerService() {
        // Starts completely empty: populated only when real cluster workflows/orders execute
    }

    public List<SagaExecution> getAllSagas() {
        return sagaStore.values().stream()
            .sorted(Comparator.comparing(SagaExecution::startedAt).reversed())
            .toList();
    }

    public Optional<SagaExecution> getSagaById(String sagaId) {
        return Optional.ofNullable(sagaStore.get(sagaId));
    }

    public void recordSaga(SagaExecution saga) {
        sagaStore.put(saga.sagaId(), saga);
    }

    public void clearAll() {
        sagaStore.clear();
    }
}
