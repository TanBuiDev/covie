package vn.covie.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies the cookie-CSRF contract before cookie authentication can reach a mutation endpoint. */
@Component
final class BrowserCookieCsrfFilter extends OncePerRequestFilter {
    private final BrowserCsrfRequestMatcher matcher; private final CookieCsrfTokenRepository tokens;
    private final AuthProperties properties; private final ApiAccessDeniedHandler denied;
    BrowserCookieCsrfFilter(BrowserCsrfRequestMatcher matcher, CookieCsrfTokenRepository tokens, AuthProperties properties, ApiAccessDeniedHandler denied) {
        this.matcher = matcher; this.tokens = tokens; this.properties = properties; this.denied = denied;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!matcher.matches(request) || !hasAuthCookie(request)) { chain.doFilter(request, response); return; }
        CsrfToken expected = tokens.loadToken(request);
        if (expected == null) { denied.handle(request, response, new MissingCsrfTokenException("CSRF token is missing")); return; }
        String submitted = request.getHeader(expected.getHeaderName());
        if (!expected.getToken().equals(submitted)) { denied.handle(request, response, new InvalidCsrfTokenException(expected, submitted)); return; }
        chain.doFilter(request, response);
    }
    private boolean hasAuthCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        return Arrays.stream(request.getCookies()).anyMatch(cookie -> cookie.getName().equals(properties.cookies().accessName())
                || cookie.getName().equals(properties.cookies().refreshName()) || cookie.getName().equals(properties.cookies().actionName()));
    }
}
