package vn.covie.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.web.RequestIdAccessor;

@Component
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    public GoogleOAuthFailureHandler(AuditLogWriter audit, RequestIdAccessor requestIds) { this.audit = audit; this.requestIds = requestIds; }
    @Override public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        try { audit.securityFailure("GOOGLE_LOGIN_FAILED", requestIds.current(), Map.of("category", "oauth_callback_failed")); }
        finally { GoogleOAuthSuccessHandler.invalidateHandshakeSession(request); }
        response.sendRedirect("/login?error=google_auth_failed");
    }
}
