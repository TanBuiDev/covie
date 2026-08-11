package vn.covie.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiErrorResponse;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final ObjectMapper MAPPER = new ObjectMapper(); private final RequestIdAccessor requestIds;
    public ApiAuthenticationEntryPoint(RequestIdAccessor requestIds) { this.requestIds=requestIds; }
    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), ApiErrorResponse.of(ErrorCode.AUTH_SESSION_INVALID,"Authentication session is no longer active.",Map.of(),Map.of(),requestIds.current()));
    }
}
