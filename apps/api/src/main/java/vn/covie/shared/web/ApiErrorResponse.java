package vn.covie.shared.web;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(ErrorBody error) {
    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            Map<String, List<String>> fields,
            String requestId) {
    }

    public static ApiErrorResponse of(
            ErrorCode code,
            String message,
            Map<String, Object> details,
            Map<String, List<String>> fields,
            String requestId) {
        return new ApiErrorResponse(new ErrorBody(
                code.name(), message, Map.copyOf(details), Map.copyOf(fields), requestId));
    }
}
