package com.ravi.product.presentation.rest.error;

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
 * Standardized RFC 7807 ProblemDetail Exception Handler for Product Service.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SERVICE_NAME = "product-service";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Product request validation failed"
        );
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/product-validation-error"));
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

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Product Not Found");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/product-not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralError(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred in Product Service"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.ecommerce.demo/errors/internal-server-error"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", SERVICE_NAME);
        return problem;
    }
}
