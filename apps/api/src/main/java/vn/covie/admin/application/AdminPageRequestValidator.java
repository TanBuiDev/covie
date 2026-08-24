package vn.covie.admin.application;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public class AdminPageRequestValidator {
    private static final Set<String> SORTS = Set.of("CREATED_DESC", "CREATED_ASC", "UPDATED_DESC");
    public AdminPageRequest validate(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Admin page or size is invalid.");
        String normalized = sort == null || sort.isBlank() ? "CREATED_DESC" : sort;
        if (!SORTS.contains(normalized)) throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Admin sort is not allowed.");
        return new AdminPageRequest(page, size, normalized);
    }
    public record AdminPageRequest(int page, int size, String sort) {}
}
