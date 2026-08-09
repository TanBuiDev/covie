package vn.covie.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthSecurityConfigurationTest {
    private final AuthProperties properties = new AuthProperties("https://covie.test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10), new AuthProperties.Jwt("key", null, null), new AuthProperties.Cookies(false, "Lax", "covie_at", "covie_rt", "covie_auth_action"));

    @Test void ignoresCookieAccessTokenInAuthNamespace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh"); request.setCookies(new Cookie("covie_at", "stale"));
        assertThat(new CookieOrHeaderBearerTokenResolver(properties).resolve(request)).isNull();
    }
    @Test void usesCookieAccessTokenForProtectedApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me"); request.setCookies(new Cookie("covie_at", "access"));
        assertThat(new CookieOrHeaderBearerTokenResolver(properties).resolve(request)).isEqualTo("access");
    }
    @Test void requiresCsrfForUnsafeBrowserRequestsButNotBearerClients() {
        BrowserCsrfRequestMatcher matcher = new BrowserCsrfRequestMatcher();
        assertThat(matcher.matches(new MockHttpServletRequest("POST", "/api/v1/auth/login"))).isTrue();
        MockHttpServletRequest bearer = new MockHttpServletRequest("POST", "/api/v1/me"); bearer.addHeader("Authorization", "Bearer token");
        assertThat(matcher.matches(bearer)).isFalse();
    }
}
