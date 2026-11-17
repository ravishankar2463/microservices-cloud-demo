package com.ravi.controlcenter.model;

import java.util.List;
import java.util.Map;

/**
 * Concrete delta difference between Before and After infrastructure states.
 */
public record InfrastructureDiff(
    List<String> highlights,
    Map<String, String> deltaDetails
) {
}
