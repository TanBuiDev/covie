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
import vn.covie.auth.api.dto.ResetPasswordWithGoogleRequest;
import vn.covie.auth.application.AuthApplicationService;
import vn.covie.auth.application.EmailPasswordLoginService;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.application.PasswordRecoveryService;
import vn.covie.auth.application.RefreshTokenService;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.auth.security.IssuedAccessToken;
import vn.covie.auth.security.SecurityCookieService;
import vn.covie.shared.web.RequestIdAccessor;

class PasswordRecoveryControllerTest {
    @Test
    void exchangesPasswordResetActionTicketForNewBrowserSession() {
        PasswordRecoveryService recovery = mock(PasswordRecoveryService.class);
        SecurityCookieService cookies = mock(SecurityCookieService.class);
        CsrfTokenLifecycleService csrf = mock(CsrfTokenLifecycleService.class);
        RequestIdAccessor requestIds = mock(RequestIdAccessor.class);
        IssuedSession issued = new IssuedSession(UUID.randomUUID(), "new-refresh", Instant.parse("2026-09-01T00:00:00Z"), new IssuedAccessToken("new-access", Instant.parse("2026-08-10T00:00:00Z")));
        when(recovery.resetWithGoogle(eq("reset-ticket"), eq("correct horse battery staple"), any())).thenReturn(issued);
        AuthController controller = new AuthController(mock(AuthApplicationService.class), mock(EmailPasswordLoginService.class), recovery, mock(RefreshTokenService.class), mock(SessionManagementService.class), mock(CurrentActorProvider.class), cookies, csrf, requestIds);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.resetPasswordWithGoogle("reset-ticket", new ResetPasswordWithGoogleRequest("correct horse battery staple"), request, response);

        assertThat(result.getBody().sessionId()).isEqualTo(issued.sessionId());
        verify(cookies).writeAccessCookie(response, "new-access", issued.accessToken().expiresAt());
        verify(cookies).writeRefreshCookie(response, "new-refresh", issued.refreshExpiresAt());
        verify(cookies).clearActionTicketCookie(response);
        verify(csrf).invalidate(request, response);
    }
}
