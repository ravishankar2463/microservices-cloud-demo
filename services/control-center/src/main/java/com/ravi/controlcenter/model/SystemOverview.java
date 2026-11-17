package com.ravi.controlcenter.model;

import java.time.Instant;
import java.util.List;

public record SystemOverview(
    int totalServices,
    int runningServices,
    int unhealthyServices,
    double requestsPerSec,
    double avgResponseTimeMs,
    int kafkaTopicsCount,
    String systemHealthStatus,  // HEALTHY, DEGRADED, CRITICAL
    String environment,
    List<String> activeAlerts,
    Instant timestamp
) {
}
