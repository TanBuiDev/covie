package vn.covie.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.api.dto.AuthSessionResponse;
import vn.covie.auth.api.dto.EmailPasswordLoginRequest;
import vn.covie.auth.api.dto.SetOnboardingPasswordRequest;
import vn.covie.auth.application.AuthApplicationService;
import vn.covie.auth.application.EmailPasswordLoginService;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.application.RefreshTokenService;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.SecurityCookieService;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.RequestIdAccessor;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthApplicationService auth;
    private final EmailPasswordLoginService emailPasswordLogin;
    private final RefreshTokenService refreshTokens;
    private final SessionManagementService sessionManagement;
    private final CurrentActorProvider actorProvider;
    private final SecurityCookieService cookies;
    private final CsrfTokenLifecycleService csrf;
    private final RequestIdAccessor requestIds;
    public AuthController(AuthApplicationService auth, EmailPasswordLoginService emailPasswordLogin, RefreshTokenService refreshTokens, SessionManagementService sessionManagement, CurrentActorProvider actorProvider, SecurityCookieService cookies, CsrfTokenLifecycleService csrf, RequestIdAccessor requestIds) {
        this.auth = auth; this.emailPasswordLogin = emailPasswordLogin; this.refreshTokens = refreshTokens; this.sessionManagement=sessionManagement; this.actorProvider=actorProvider; this.cookies = cookies; this.csrf = csrf; this.requestIds = requestIds;
    }
    @PostMapping("/onboarding/set-password")
    public ResponseEntity<AuthSessionResponse> setOnboardingPassword(@CookieValue(name = "covie_auth_action", required = false) String rawTicket,
            @Valid @RequestBody SetOnboardingPasswordRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        IssuedSession issued = auth.completeOnboarding(rawTicket, request.password(), requestIds.current());
        cookies.writeAccessCookie(response, issued.accessToken().value(), issued.accessToken().expiresAt());
        cookies.writeRefreshCookie(response, issued.rawRefreshToken(), issued.refreshExpiresAt());
        cookies.clearActionTicketCookie(response);
        csrf.invalidate(servletRequest, response);
        return ResponseEntity.ok(AuthSessionResponse.from(issued));
    }
    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody EmailPasswordLoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        IssuedSession issued = emailPasswordLogin.login(request.email(), request.password(), DeviceMetadata.from(servletRequest), requestIds.current());
        cookies.writeAccessCookie(response, issued.accessToken().value(), issued.accessToken().expiresAt());
        cookies.writeRefreshCookie(response, issued.rawRefreshToken(), issued.refreshExpiresAt());
        csrf.invalidate(servletRequest, response);
        return AuthSessionResponse.from(issued);
    }
    @PostMapping("/refresh")
    public AuthSessionResponse refresh(@CookieValue(name = "covie_rt", required = false) String rawRefreshToken, HttpServletRequest servletRequest, HttpServletResponse response) {
        IssuedSession issued = refreshTokens.refresh(rawRefreshToken, requestIds.current());
        cookies.writeAccessCookie(response, issued.accessToken().value(), issued.accessToken().expiresAt());
        cookies.writeRefreshCookie(response, issued.rawRefreshToken(), issued.refreshExpiresAt());
        csrf.invalidate(servletRequest, response);
        return AuthSessionResponse.from(issued);
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "covie_rt", required = false) String rawRefreshToken, HttpServletRequest servletRequest, HttpServletResponse response) {
        try { sessionManagement.revokeForLogout(actorProvider.currentActor().sessionId()); }
        catch (ApiException ignored) { refreshTokens.sessionIdFor(rawRefreshToken).ifPresent(sessionManagement::revokeForLogout); }
        cookies.clearAllAuthCookies(response); csrf.invalidate(servletRequest, response);
        return ResponseEntity.noContent().build();
    }
}
