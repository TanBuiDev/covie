package vn.covie.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

@Component
public final class BrowserCsrfRequestMatcher implements RequestMatcher {
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());
    @Override public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) return false;
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}
