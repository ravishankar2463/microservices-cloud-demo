package com.ravi.controlcenter.model;

/**
 * Result of attempting to launch a local editor on the host machine.
 *
 * @param status SUCCESS, ERROR, or BLOCKED
 * @param editor VS_CODE, NOTEPAD, or NONE
 * @param filePath The relative path requested
 * @param absolutePath The canonical absolute path on the host filesystem
 * @param clientLaunchUrl Direct browser URI scheme (e.g. vscode://file/<path>:<line>)
 * @param message Informational message for UI toast notification
 */
public record EditorLaunchResponse(
    String status,
    String editor,
    String filePath,
    String absolutePath,
    String clientLaunchUrl,
    String message
) {
    public static EditorLaunchResponse success(String editor, String filePath, String absolutePath, String clientLaunchUrl, String message) {
        return new EditorLaunchResponse("SUCCESS", editor, filePath, absolutePath, clientLaunchUrl, message);
    }

    public static EditorLaunchResponse error(String editor, String filePath, String absolutePath, String clientLaunchUrl, String message) {
        return new EditorLaunchResponse("ERROR", editor, filePath, absolutePath, clientLaunchUrl, message);
    }
}
