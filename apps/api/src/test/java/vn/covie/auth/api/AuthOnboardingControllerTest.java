package vn.covie.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import vn.covie.auth.api.dto.SetOnboardingPasswordRequest;
import vn.covie.auth.application.AuthApplicationService;
import vn.covie.auth.application.EmailPasswordLoginService;
import vn.covie.auth.application.RefreshTokenService;
import vn.covie.auth.application.PasswordRecoveryService;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.IssuedAccessToken;
import vn.covie.auth.security.SecurityCookieService;
import vn.covie.shared.web.RequestIdAccessor;

class AuthOnboardingControllerTest {
    @Test
    void issuesBrowserCookiesAndNeverReturnsRawTokens() {
        AuthApplicationService service = mock(AuthApplicationService.class);
        SecurityCookieService cookies = mock(SecurityCookieService.class);
        CsrfTokenLifecycleService csrf = mock(CsrfTokenLifecycleService.class);
        RequestIdAccessor requestIds = mock(RequestIdAccessor.class);
        UUID sessionId = UUID.randomUUID();
        IssuedSession issued = new IssuedSession(sessionId, "raw-refresh", Instant.parse("2026-09-01T00:00:00Z"), new IssuedAccessToken("raw-access", Instant.parse("2026-08-09T01:00:00Z")));
        when(service.completeOnboarding(eq("action"), eq("correct horse battery staple"), any())).thenReturn(issued);
        AuthController controller = new AuthController(service, mock(EmailPasswordLoginService.class), mock(PasswordRecoveryService.class), mock(RefreshTokenService.class), mock(SessionManagementService.class), mock(CurrentActorProvider.class), cookies, csrf, requestIds);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.setOnboardingPassword("action", new SetOnboardingPasswordRequest("correct horse battery staple"), request, response);

        assertThat(result.getBody().sessionId()).isEqualTo(sessionId);
        verify(cookies).writeAccessCookie(response, "raw-access", issued.accessToken().expiresAt());
        verify(cookies).writeRefreshCookie(response, "raw-refresh", issued.refreshExpiresAt());
        verify(cookies).clearActionTicketCookie(response);
        verify(csrf).invalidate(request, response);
    }
}
