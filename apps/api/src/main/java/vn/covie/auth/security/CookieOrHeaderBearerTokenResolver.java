package vn.covie.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public final class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final AuthProperties properties;
    public CookieOrHeaderBearerTokenResolver(AuthProperties properties) { this.properties = properties; }
    @Override public String resolve(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) return headerResolver.resolve(request);
        if (request.getRequestURI().startsWith("/api/v1/auth/")) return null;
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> properties.cookies().accessName().equals(cookie.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }
}
