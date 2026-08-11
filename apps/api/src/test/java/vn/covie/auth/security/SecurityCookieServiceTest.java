package vn.covie.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityCookieServiceTest {
    private final SecurityCookieService cookies = new SecurityCookieService(new AuthProperties(
            "https://covie.test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10),
            new AuthProperties.Jwt("key", null, null),
            new AuthProperties.Cookies(false, "Lax", "covie_at", "covie_rt", "covie_auth_action")));

    @Test
    void scopesRefreshAndActionCookiesToAuthEndpoints() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Instant expiry = Instant.now().plus(Duration.ofDays(30));

        cookies.writeRefreshCookie(response, "opaque-refresh", expiry);
        cookies.writeActionTicketCookie(response, "opaque-ticket", expiry);

        assertThat(response.getHeaders("Set-Cookie"))
                .allSatisfy(value -> assertThat(value).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth"));
    }

    @Test
    void clearsAllAuthCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.clearAllAuthCookies(response);

        assertThat(response.getHeaders("Set-Cookie")).hasSize(3).allSatisfy(value -> assertThat(value).contains("Max-Age=0"));
    }
}
