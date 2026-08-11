package vn.covie.shared.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }
}
