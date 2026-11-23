package com.ravi.controlcenter.model;

/**
 * Structured code reference representing a concrete implementation file in the repository.
 *
 * @param label Human-readable label (e.g., "Order Saga Orchestrator")
 * @param relativePath Exact relative path from the repository clone root
 * @param line Target line number in the source file
 * @param description Detailed architectural role or method description
 */
public record CodeReference(
    String label,
    String relativePath,
    int line,
    String description
) {
    public CodeReference(String label, String relativePath, int line) {
        this(label, relativePath, line, "");
    }
}
