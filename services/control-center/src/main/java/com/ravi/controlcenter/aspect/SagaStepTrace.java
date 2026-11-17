package com.ravi.controlcenter.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation marking distributed saga step operations for Spring AOP JoinPoint
 * interception, performance timing, parameter inspection, and Learning Mode trace capture.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaStepTrace {
    String step();
    String targetBean() default "";
    String[] annotations() default {};
}
