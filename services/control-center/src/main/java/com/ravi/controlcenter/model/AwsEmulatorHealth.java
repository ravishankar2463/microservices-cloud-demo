package com.ravi.controlcenter.model;

import java.util.List;
import java.util.Map;

public record AwsEmulatorHealth(
    String edition,
    String version,
    String status, // ONLINE, OFFLINE
    int totalServices,
    int availableCount,
    int runningCount,
    int disabledCount,
    List<AwsServiceDetail> activeServices,
    List<AwsServiceDetail> allServices,
    String endpointUrl,
    Map<String, String> rawServices,
    String message
) {
    public record AwsServiceDetail(
        String name,
        String category,
        String status,
        boolean active,
        String description,
        String architectureUsage,
        String cliCommand
    ) {
        public String demoUsage() { return architectureUsage; }
    }
}
