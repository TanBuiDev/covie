package vn.covie.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.covie.auth.api.GoogleAuthController;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.GoogleAuthService;
import vn.covie.auth.application.GoogleClaims;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.SecurityCookieService;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final GoogleAuthService googleAuth;
    private final SecurityCookieService cookies;
    private final CsrfTokenLifecycleService csrf;

    public GoogleOAuthSuccessHandler(GoogleAuthService googleAuth, SecurityCookieService cookies, CsrfTokenLifecycleService csrf) {
        this.googleAuth = googleAuth; this.cookies = cookies; this.csrf = csrf;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) throw new ServletException("OIDC user is required");
        GoogleClaims claims = new GoogleClaims(oidcUser.getSubject(), oidcUser.getEmail(), oidcUser.getClaim("email_verified"), oidcUser.getFullName());
        GoogleAuthIntent intent = intent(request);
        try {
            GoogleAuthResult result = intent == GoogleAuthIntent.PASSWORD_RESET
                    ? googleAuth.handlePasswordReset(claims)
                    : googleAuth.handleLogin(claims, DeviceMetadata.from(request));
            csrf.invalidate(request, response);
            if (result instanceof GoogleAuthResult.ExistingUser existing) {
                cookies.writeAccessCookie(response, existing.session().accessToken().value(), existing.session().accessToken().expiresAt());
                cookies.writeRefreshCookie(response, existing.session().rawRefreshToken(), existing.session().refreshExpiresAt());
                response.sendRedirect("/");
            } else if (result instanceof GoogleAuthResult.Onboarding onboarding) {
                cookies.writeActionTicketCookie(response, onboarding.ticket().rawToken(), onboarding.ticket().expiresAt());
                response.sendRedirect("/onboarding/password");
            } else if (result instanceof GoogleAuthResult.PasswordReset reset) {
                cookies.writeActionTicketCookie(response, reset.ticket().rawToken(), reset.ticket().expiresAt());
                response.sendRedirect("/reset-password");
            } else {
                throw new ServletException("Unknown Google authentication result");
            }
        } finally {
            invalidateHandshakeSession(request);
        }
    }

    private GoogleAuthIntent intent(HttpServletRequest request) {
        Object value = request.getSession(false) == null ? null : request.getSession(false).getAttribute(GoogleAuthController.SESSION_INTENT);
        if (value instanceof String text) try { return GoogleAuthIntent.valueOf(text); } catch (IllegalArgumentException ignored) { }
        return GoogleAuthIntent.LOGIN;
    }
    static void invalidateHandshakeSession(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }
}
