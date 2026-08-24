package vn.covie.shared.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.covie.ratelimit.RateLimitExceededException;

@RestControllerAdvice
public final class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(ApiErrorResponse.of(exception.code(), exception.getMessage(), exception.details(), Map.of(), requestId()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiErrorResponse.of(
                exception.code(), exception.getMessage(), exception.details(), Map.of(), requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields
                .computeIfAbsent(error.getField(), ignored -> new ArrayList<>())
                .add(error.getDefaultMessage() == null ? "INVALID_VALUE" : error.getDefaultMessage()));

        return ResponseEntity.unprocessableEntity().body(ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, "Request validation failed.", Map.of(), fields, requestId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                ErrorCode.MALFORMED_REQUEST, "Request body is malformed.", Map.of(), Map.of(), requestId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        if ("/api/v1/catalog/items".equals(request.getRequestURI())) {
            return ResponseEntity.unprocessableEntity().body(ApiErrorResponse.of(
                    ErrorCode.CATALOG_FILTER_INVALID, "Catalog discovery filters are invalid.", Map.of(), Map.of(), requestId()));
        }
        return ResponseEntity.unprocessableEntity().body(ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, "Request validation failed.", Map.of(), Map.of(), requestId()));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ApiErrorResponse> handleOptimisticLockFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                "Resource version has changed.",
                Map.of(),
                Map.of(),
                requestId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled API exception requestId={} path={}", requestId(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", Map.of(), Map.of(), requestId()));
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
