package com.ravi.controlcenter.model;

public record TopologyEdge(
    String id,
    String source,
    String target,
    String type,          // SYNC_REST, ASYNC_KAFKA, PERSISTENCE, CACHE, CLOUD_INTEGRATION
    String label,
    boolean animated
) {
}
