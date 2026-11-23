package com.ravi.inventory.presentation.rest.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Standardized RFC 7807 ProblemDetail Exception Handler for Inventory Service.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SERVICE_NAME = "inventory-service";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Inventory reservation validation failed"
        );
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/inventory-validation-error"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);

        Map<String, String> invalidParams = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                invalidParams.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("invalidParams", invalidParams);

        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Stock State Conflict");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/inventory-conflict"));
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
        problem.setTitle("Inventory Item Not Found");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/inventory-not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralError(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred in Inventory Service"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/internal-server-error"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }
}
