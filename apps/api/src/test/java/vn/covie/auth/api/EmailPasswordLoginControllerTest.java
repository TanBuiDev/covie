package vn.covie.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.api.dto.EmailPasswordLoginRequest;
import vn.covie.auth.application.AuthApplicationService;
import vn.covie.auth.application.EmailPasswordLoginService;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.application.RefreshTokenService;
import vn.covie.auth.application.PasswordRecoveryService;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.IssuedAccessToken;
import vn.covie.auth.security.SecurityCookieService;
import vn.covie.shared.web.RequestIdAccessor;

class EmailPasswordLoginControllerTest {
    @Test
    void writesAccessRefreshCookiesAndInvalidatesPreLoginCsrfToken() {
        EmailPasswordLoginService login = mock(EmailPasswordLoginService.class);
        SecurityCookieService cookies = mock(SecurityCookieService.class);
        CsrfTokenLifecycleService csrf = mock(CsrfTokenLifecycleService.class);
        IssuedSession issued = new IssuedSession(UUID.randomUUID(), "refresh", Instant.parse("2026-09-01T00:00:00Z"), new IssuedAccessToken("access", Instant.parse("2026-08-10T00:00:00Z")));
        when(login.login(eq("minh@example.com"), eq("correct horse battery staple"), any(), any())).thenReturn(issued);
        AuthController controller = new AuthController(mock(AuthApplicationService.class), login, mock(PasswordRecoveryService.class), mock(RefreshTokenService.class), mock(SessionManagementService.class), mock(CurrentActorProvider.class), cookies, csrf, mock(RequestIdAccessor.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(new EmailPasswordLoginRequest("minh@example.com", "correct horse battery staple"), request, response);

        verify(cookies).writeAccessCookie(response, "access", issued.accessToken().expiresAt());
        verify(cookies).writeRefreshCookie(response, "refresh", issued.refreshExpiresAt());
        verify(csrf).invalidate(request, response);
    }
}
