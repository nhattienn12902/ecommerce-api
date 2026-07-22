package com.nhattienn.ecommerce.common.exception;

import com.nhattienn.ecommerce.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildDebug(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return buildDebug(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found.", request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    // Bắt BusinessException VÀ mọi subclass (InsufficientStockException...) → 422
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication failed.", request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to access this resource.", request);
    }



    // @Valid trên @RequestBody fail → 400, kèm danh sách field lỗi
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        String traceId = traceId();
        log.warn("VALIDATION_ERROR [{}]: {} field error(s) at {}",
                traceId, fieldErrors.size(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "Validation failed.",
                request.getRequestURI(), traceId, fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // FALLBACK — last resort. TUYỆT ĐỐI không để lộ stack trace / class name ra client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = traceId();
        // Stack trace đầy đủ CHỈ ghi ở server-side log, không đi ra response
        log.error("INTERNAL_ERROR [{}] at {}", traceId, request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "An unexpected error occurred. Please reference the traceId when contacting support.",
                request.getRequestURI(), traceId);
        return ResponseEntity.internalServerError().body(body);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error,
                                                String message, HttpServletRequest request) {
        String traceId = traceId();
        log.warn("{} [{}]: {} at {}", error, traceId, message, request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(status.value(), error, message, request.getRequestURI(), traceId);
        return ResponseEntity.status(status).body(body);
    }

    private String traceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<ErrorResponse> buildDebug(HttpStatus status, String error,
                                                  String message, HttpServletRequest request) {
    String traceId = traceId();
    log.debug("{} [{}]: {} at {}", error, traceId, message, request.getRequestURI());
    ErrorResponse body = ErrorResponse.of(status.value(), error, message, request.getRequestURI(), traceId);
    return ResponseEntity.status(status).body(body);
}
    
}