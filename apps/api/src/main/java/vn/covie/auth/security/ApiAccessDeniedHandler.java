package vn.covie.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiErrorResponse;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper(); private final RequestIdAccessor requestIds;
    public ApiAccessDeniedHandler(RequestIdAccessor requestIds) { this.requestIds=requestIds; }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        boolean csrf=exception instanceof MissingCsrfTokenException || exception instanceof InvalidCsrfTokenException;
        response.setStatus(csrf ? 403 : 403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), ApiErrorResponse.of(csrf ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED, csrf ? "CSRF token is invalid." : "Access denied.",Map.of(),Map.of(),requestIds.current()));
    }
}
