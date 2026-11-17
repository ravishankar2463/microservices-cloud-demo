package com.ravi.controlcenter.model;

public record InfraNodeInfo(
    String id,
    String name,
    String platform,       // KUBERNETES or DOCKER
    String state,          // RUNNING, CRASH_LOOP, EXITED, PENDING, ERROR
    String statusText,     // e.g. "Running", "CrashLoopBackOff", "Exited (1)"
    int restartCount,
    String age,
    String failureReason,  // e.g. "CrashLoopBackOff", "ContainersNotReady"
    String failureMessage, // Detailed error message from k8s status or docker
    int exitCode,
    String readyStatus     // e.g. "1/1", "0/1"
) {
}
