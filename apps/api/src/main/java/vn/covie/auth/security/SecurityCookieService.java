package vn.covie.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class SecurityCookieService {
    private static final String AUTH_PATH = "/api/v1/auth";
    private final AuthProperties properties;

    public SecurityCookieService(AuthProperties properties) { this.properties = properties; }

    public void writeAccessCookie(HttpServletResponse response, String rawJwt, Instant expiresAt) { write(response, properties.cookies().accessName(), rawJwt, "/", expiresAt, properties.accessTokenTtl(), true); }
    public void writeRefreshCookie(HttpServletResponse response, String rawRefresh, Instant expiresAt) { write(response, properties.cookies().refreshName(), rawRefresh, AUTH_PATH, expiresAt, properties.refreshTokenTtl(), true); }
    public void writeActionTicketCookie(HttpServletResponse response, String rawTicket, Instant expiresAt) { write(response, properties.cookies().actionName(), rawTicket, AUTH_PATH, expiresAt, properties.actionTicketTtl(), true); }
    public void clearAccessCookie(HttpServletResponse response) { clear(response, properties.cookies().accessName(), "/"); }
    public void clearRefreshCookie(HttpServletResponse response) { clear(response, properties.cookies().refreshName(), AUTH_PATH); }
    public void clearActionTicketCookie(HttpServletResponse response) { clear(response, properties.cookies().actionName(), AUTH_PATH); }
    public void clearAllAuthCookies(HttpServletResponse response) { clearAccessCookie(response); clearRefreshCookie(response); clearActionTicketCookie(response); }

    private void write(HttpServletResponse response, String name, String value, String path, Instant expiresAt, Duration maximum, boolean httpOnly) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        Duration maxAge = remaining.isNegative() ? Duration.ZERO : remaining.compareTo(maximum) > 0 ? maximum : remaining;
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(httpOnly).secure(properties.cookies().secure()).sameSite(properties.cookies().sameSite()).path(path).maxAge(maxAge).build().toString());
    }

    private void clear(HttpServletResponse response, String name, String path) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "").httpOnly(true).secure(properties.cookies().secure()).sameSite(properties.cookies().sameSite()).path(path).maxAge(Duration.ZERO).build().toString());
    }
}
