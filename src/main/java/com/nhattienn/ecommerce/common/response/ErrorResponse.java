package com.nhattienn.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors,
        String traceId
) {
    public record FieldError(String field, String message) {}

    public static ErrorResponse of(int status, String error, String message,
                                   String path, String traceId) {
        return new ErrorResponse(false, Instant.now(), status, error, message, path, null, traceId);
    }

    public static ErrorResponse of(int status, String error, String message,
                                   String path, String traceId, List<FieldError> errors) {
        return new ErrorResponse(false, Instant.now(), status, error, message, path, errors, traceId);
    }
}