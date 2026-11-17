package com.ravi.controlcenter.model;

import java.util.List;

/**
 * Educational masterclass dossier for a distributed architectural pattern.
 * Details the concrete action, rationale, distributed significance, pros/cons tradeoffs,
 * and a dual-tier hyperscale enterprise scaling blueprint.
 */
public record ArchitecturalDossier(
    String patternName,
    String category,
    String whatItDid,
    String whyItDidIt,
    String architecturalSignificance,
    List<String> pros,
    List<String> cons,
    HyperscaleBlueprint hyperscaleBlueprint
) {
    public record HyperscaleBlueprint(
        String currentScaleTier,
        String currentMechanism,
        String bottleneckAtScale,
        String enterpriseScaleTier,
        String enterpriseSolution,
        String migrationPath
    ) {}
}
