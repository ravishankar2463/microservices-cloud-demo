package com.ravi.controlcenter.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Model representing the real HTTP response and state deltas captured from a live cluster dispatch.
 */
public record LiveDispatchResponse(
    int statusCode,
    String statusText,
    Map<String, List<String>> headers,
    String responseBody,
    long durationMs,
    String curlCommand,
    String targetUrl,
    String correlationId,
    String orderId,
    InfrastructureSnapshot beforeSnapshot,
    InfrastructureSnapshot afterSnapshot,
    InfrastructureDiff stateDiff,
    Instant executedAt
) {}
