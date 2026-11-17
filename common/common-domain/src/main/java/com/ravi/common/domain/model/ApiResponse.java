package com.ravi.common.domain.model;

import java.time.Instant;

/**
 * Standard immutable API response envelope using Java 21 Record.
 *
 * @param <T> Payload data type
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String errorMessage) {
        return new ApiResponse<>(false, null, errorMessage, Instant.now());
    }
}
