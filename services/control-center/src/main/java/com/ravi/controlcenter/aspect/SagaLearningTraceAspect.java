package com.ravi.controlcenter.aspect;

import com.ravi.controlcenter.model.AopDebugTrace;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AOP Aspect providing execution interception on DEBUG [Learning Mode].
 * Captures JoinPoint method signatures, parameter JSON, virtual thread identities,
 * execution timing, and Spring annotations (@Transactional, @KafkaListener, etc.).
 */
@Aspect
@Component
public class SagaLearningTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(SagaLearningTraceAspect.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory registry of traces keyed by step or correlation ID
    private final Map<String, AopDebugTrace> traceBuffer = new ConcurrentHashMap<>();

    @Around("@annotation(traceAnnotation)")
    public Object interceptSagaStep(ProceedingJoinPoint joinPoint, SagaStepTrace traceAnnotation) throws Throwable {
        long startNanos = System.nanoTime();
        String stepName = traceAnnotation.step();
        String beanName = traceAnnotation.targetBean().isBlank()
            ? joinPoint.getTarget().getClass().getSimpleName()
            : traceAnnotation.targetBean();

        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String methodSig = sig.getMethod() != null ? sig.getMethod().toGenericString() : sig.toLongString();
        String threadName = Thread.currentThread().getName();
        boolean isVirtual = Thread.currentThread().isVirtual();

        List<String> logs = new ArrayList<>();
        logs.add(String.format("[AOP DEBUG] %s -> Entering JoinPoint: %s", threadName, sig.toShortString()));

        String paramsJson = "{}";
        try {
            paramsJson = objectMapper.writeValueAsString(joinPoint.getArgs());
            logs.add("[AOP DEBUG] Bound parameters: " + paramsJson);
        } catch (Exception e) {
            paramsJson = Arrays.toString(joinPoint.getArgs());
        }

        Object result;
        try {
            result = joinPoint.proceed();
            long elapsedNanos = System.nanoTime() - startNanos;
            logs.add(String.format("[AOP DEBUG] JoinPoint returned successfully in %d µs", elapsedNanos / 1000));

            String returnJson = "{}";
            try {
                returnJson = objectMapper.writeValueAsString(result);
            } catch (Exception ignored) {
                returnJson = String.valueOf(result);
            }

            List<String> annotations = new ArrayList<>(Arrays.asList(traceAnnotation.annotations()));
            if (annotations.isEmpty()) {
                annotations.add("@Transactional(propagation = REQUIRED, isolation = READ_COMMITTED)");
            }

            AopDebugTrace trace = new AopDebugTrace(
                beanName,
                joinPoint.toShortString(),
                methodSig,
                paramsJson,
                returnJson,
                elapsedNanos,
                threadName,
                isVirtual,
                annotations,
                logs
            );
            traceBuffer.put(stepName, trace);

            return result;
        } catch (Throwable t) {
            long elapsedNanos = System.nanoTime() - startNanos;
            logs.add(String.format("[AOP DEBUG] Exception intercepted: %s - %s", t.getClass().getSimpleName(), t.getMessage()));

            AopDebugTrace trace = new AopDebugTrace(
                beanName,
                joinPoint.toShortString(),
                methodSig,
                paramsJson,
                "{\"error\": \"" + t.getMessage() + "\"}",
                elapsedNanos,
                threadName,
                isVirtual,
                Arrays.asList(traceAnnotation.annotations()),
                logs
            );
            traceBuffer.put(stepName, trace);
            throw t;
        }
    }

    public AopDebugTrace buildSyntheticTrace(
            String stepName,
            String targetBean,
            String methodSignature,
            Object params,
            Object returnVal,
            long durationMs,
            List<String> annotations,
            List<String> customLogs) {

        long nanos = durationMs * 1_000_000L;
        String threadName = Thread.currentThread().getName();
        boolean isVirtual = Thread.currentThread().isVirtual();

        String paramsJson = "{}";
        String returnJson = "{}";
        try {
            paramsJson = objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            paramsJson = String.valueOf(params);
        }
        try {
            returnJson = objectMapper.writeValueAsString(returnVal);
        } catch (Exception e) {
            returnJson = String.valueOf(returnVal);
        }

        List<String> logs = new ArrayList<>();
        logs.add(String.format("[AOP DEBUG][%s] Entering JoinPoint: execution(%s)", threadName, methodSignature));
        logs.add("[AOP DEBUG] Aspect intercepted method with Spring annotations: " + annotations);
        logs.add("[AOP DEBUG] Bound arguments: " + paramsJson);
        if (customLogs != null) {
            logs.addAll(customLogs);
        }
        logs.add(String.format("[AOP DEBUG] JoinPoint completed in %d ms (VirtualThread=%b)", durationMs, isVirtual));

        return new AopDebugTrace(
            targetBean,
            "execution(" + methodSignature + ")",
            methodSignature,
            paramsJson,
            returnJson,
            nanos,
            threadName,
            isVirtual,
            annotations,
            logs
        );
    }

    public AopDebugTrace getTraceForStep(String stepName) {
        return traceBuffer.get(stepName);
    }

    public void clearTraces() {
        traceBuffer.clear();
    }
}
