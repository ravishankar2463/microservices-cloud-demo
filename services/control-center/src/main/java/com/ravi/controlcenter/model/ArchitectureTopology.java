package com.ravi.controlcenter.model;

import java.util.List;

public record ArchitectureTopology(
    List<ServiceNode> nodes,
    List<TopologyEdge> edges
) {
}
