package com.ravi.controlcenter.model;

import java.util.List;

public record ServiceNode(
    String id,
    String name,
    String type,          // MICROSERVICE, DATABASE, BROKER, CACHE, IAM, CLOUD_EMULATOR
    int port,
    String baseUrl,
    String status,        // UP, DOWN, UNKNOWN
    long responseTimeMs,
    String description,
    String database,
    List<String> topicsConsumed,
    List<String> topicsProduced,
    List<String> dependencies
) {
}
