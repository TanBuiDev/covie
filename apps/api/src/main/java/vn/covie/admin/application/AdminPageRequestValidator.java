package vn.covie.admin.application;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class AdminPageRequestValidator {
    public AdminPageRequest validate(int page, int size, String sort, Set<String> allowed, String defaultSort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Admin page or size is invalid.");
        }
        String normalized = sort == null || sort.isBlank() ? defaultSort : sort.strip().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Admin sort is not allowed.");
        }
        return new AdminPageRequest(page, size, normalized);
    }

    public record AdminPageRequest(int page, int size, String sort) {}
}
