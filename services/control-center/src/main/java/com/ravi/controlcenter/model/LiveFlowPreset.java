package com.ravi.controlcenter.model;

import java.util.List;

/**
 * Metadata and default payloads for a canonical live workflow preset.
 */
public record LiveFlowPreset(
    String id,
    String name,
    String category,
    String description,
    String curlCommand,
    List<PresetStep> steps
) {
    public record PresetStep(
        String stepName,
        String targetService,
        String httpMethod,
        String path,
        String defaultBodyJson,
        String expectedOutcome
    ) {}
}
