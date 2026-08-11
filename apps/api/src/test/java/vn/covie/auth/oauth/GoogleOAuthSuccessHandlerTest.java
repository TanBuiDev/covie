package vn.covie.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import vn.covie.auth.api.GoogleAuthController;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.GoogleAuthService;
import vn.covie.auth.application.IssuedAuthActionTicket;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.security.CsrfTokenLifecycleService;
import vn.covie.auth.security.SecurityCookieService;

class GoogleOAuthSuccessHandlerTest {
    @Test
    void writesOnlyActionCookieAndRedirectsNewGoogleUserToOnboarding() throws Exception {
        GoogleAuthService service = mock(GoogleAuthService.class);
        IssuedAuthActionTicket ticket = new IssuedAuthActionTicket("opaque-action", AuthActionPurpose.ONBOARDING, Instant.parse("2026-08-09T00:10:00Z"), null);
        when(service.handleLogin(any(), any())).thenReturn(new GoogleAuthResult.Onboarding(ticket));
        SecurityCookieService cookies = mock(SecurityCookieService.class);
        CsrfTokenLifecycleService csrf = mock(CsrfTokenLifecycleService.class);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(service, cookies, csrf);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("minh@example.com");
        when(oidcUser.getClaim("email_verified")).thenReturn(true);
        when(oidcUser.getFullName()).thenReturn("Minh");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(GoogleAuthController.SESSION_INTENT, GoogleAuthIntent.LOGIN.name());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, new OAuth2AuthenticationToken(oidcUser, List.of(), "google"));

        verify(cookies).writeActionTicketCookie(response, "opaque-action", ticket.expiresAt());
        verify(csrf).invalidate(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/onboarding/password");
        assertThat(request.getSession(false)).isNull();
    }
}
