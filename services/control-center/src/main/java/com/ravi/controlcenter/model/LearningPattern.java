package com.ravi.controlcenter.model;

import java.util.List;

/**
 * Microservices architectural pattern representation.
 *
 * @param id Unique pattern identifier
 * @param title Human-readable pattern title
 * @param category Architectural domain category
 * @param problem Architectural challenge or failure mode solved
 * @param concept Core architectural principle and solution
 * @param implementation How this demo project concretely implements it
 * @param services List of participating microservices
 * @param codeAnchor Legacy string reference for backwards compatibility
 * @param codeReferences Structured list of code references with relative paths and line numbers
 * @param tradeOffs Production trade-offs and considerations (Pros vs Cons)
 * @param dashboardTab Optional cross-link to relevant Control Center tab (e.g. sagas, kafka, architecture, operations)
 */
public record LearningPattern(
    String id,
    String title,
    String category,
    String problem,
    String concept,
    String implementation,
    List<String> services,
    String codeAnchor,
    List<CodeReference> codeReferences,
    String tradeOffs,
    String dashboardTab
) {
    public LearningPattern(
        String id,
        String title,
        String category,
        String problem,
        String concept,
        String implementation,
        List<String> services,
        String codeAnchor,
        String tradeOffs
    ) {
        this(id, title, category, problem, concept, implementation, services, codeAnchor, List.of(), tradeOffs, null);
    }
}
