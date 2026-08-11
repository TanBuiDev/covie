package vn.covie.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.covie.auth.application.SessionManagementService;

/** A database outage is operational, so the filter must let MVC turn it into 5xx rather than emit 401. */
class ActiveSessionFilterFailureTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void operationalSessionValidationFailurePropagatesInsteadOfBecomingUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID();
        SessionManagementService sessions = mock(SessionManagementService.class);
        ApiAuthenticationEntryPoint entryPoint = mock(ApiAuthenticationEntryPoint.class);
        when(sessions.requireActiveSessionForUser(sessionId, userId)).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        Jwt jwt = Jwt.withTokenValue("valid-jwt-token").header("alg", "none").subject(userId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("sessionId", sessionId.toString()).build();
        AbstractAuthenticationToken authentication = new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        ActiveSessionFilter filter = new ActiveSessionFilter(sessions, entryPoint);

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/me"), new MockHttpServletResponse(), mock(jakarta.servlet.FilterChain.class)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(entryPoint, never()).commence(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
