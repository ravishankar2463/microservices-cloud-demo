package com.ravi.gateway.presentation.rest.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Standardized RFC 7807 ProblemDetail Exception Handler for API Gateway.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String SERVICE_NAME = "api-gateway";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Gateway Bad Request");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/gateway-bad-request"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Downstream Route Not Found");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/route-not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralError(Exception ex) {
        log.error("Unhandled exception in API Gateway: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred in API Gateway routing"
        );
        problem.setTitle("Gateway Routing Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/gateway-error"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }
}
