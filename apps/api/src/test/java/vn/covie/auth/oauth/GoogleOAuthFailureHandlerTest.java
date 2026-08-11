package vn.covie.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.web.RequestIdAccessor;

class GoogleOAuthFailureHandlerTest {
    @Test
    void redirectsWithGenericErrorAndInvalidatesHandshakeSession() throws Exception {
        GoogleOAuthFailureHandler handler = new GoogleOAuthFailureHandler(mock(AuditLogWriter.class), mock(RequestIdAccessor.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("oauth", "temporary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new AuthenticationException("sensitive Google error") {});

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_auth_failed");
        assertThat(request.getSession(false)).isNull();
    }
}
