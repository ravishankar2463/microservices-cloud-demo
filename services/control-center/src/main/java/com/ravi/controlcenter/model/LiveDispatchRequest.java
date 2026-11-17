package com.ravi.controlcenter.model;

import java.util.Map;

/**
 * Model representing a live HTTP request to be dispatched to a running cluster microservice.
 */
public record LiveDispatchRequest(
    String targetService,   // order-service, payment-service, inventory-service, notification-service, api-gateway
    String httpMethod,      // GET, POST, PUT, DELETE
    String path,            // e.g. /api/orders, /api/payments/process
    Map<String, String> headers,
    String bodyJson,
    String correlationId,
    String orderId,
    boolean inspectInfrastructure
) {
    public LiveDispatchRequest withCorrelationId(String corrId) {
        return new LiveDispatchRequest(targetService, httpMethod, path, headers, bodyJson, corrId, orderId, inspectInfrastructure);
    }
}
