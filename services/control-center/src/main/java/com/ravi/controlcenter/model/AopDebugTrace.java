package com.ravi.controlcenter.model;

import java.util.List;

/**
 * Spring AOP execution telemetry captured when Learning Mode is active on DEBUG.
 * Provides live join point introspection, virtual thread identification,
 * parameter bindings, and intercepted execution logs.
 */
public record AopDebugTrace(
    String targetBean,
    String joinPoint,
    String methodSignature,
    String parametersJson,
    String returnValueJson,
    long durationNanos,
    String threadName,
    boolean isVirtualThread,
    List<String> annotations,
    List<String> executionLogs
) {
    public AopDebugTrace(String targetBean, String methodSignature, Object parameters, Object returnValue, List<String> annotations, List<String> executionLogs) {
        this(targetBean, "execution(* " + targetBean + "." + methodSignature + "(..))", methodSignature, String.valueOf(parameters), String.valueOf(returnValue), 1_500_000L, Thread.currentThread().getName(), Thread.currentThread().isVirtual(), annotations, executionLogs);
    }
}
