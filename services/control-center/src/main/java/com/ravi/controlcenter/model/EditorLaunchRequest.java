package com.ravi.controlcenter.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for launching a local editor (VS Code or Notepad) on a repository file.
 *
 * @param filePath Relative path from the repository clone root
 * @param line Target line number (optional, default 1)
 */
public record EditorLaunchRequest(
    @NotBlank(message = "filePath is required")
    String filePath,
    Integer line
) {
    public EditorLaunchRequest(String filePath) {
        this(filePath, 1);
    }

    public int resolvedLine() {
        return (line == null || line < 1) ? 1 : line;
    }
}
