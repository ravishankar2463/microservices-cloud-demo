package com.ravi.controlcenter.presentation.rest.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Standardized RFC 7807 ProblemDetail Exception Handler for Control Center.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SERVICE_NAME = "control-center";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Control Center Bad Request");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/control-center-bad-request"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralError(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected operational error occurred"
        );
        problem.setTitle("Operation Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/control-center-error"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }
}
