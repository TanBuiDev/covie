package vn.covie.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
public final class CsrfTokenLifecycleService {
    private final CookieCsrfTokenRepository repository;
    public CsrfTokenLifecycleService(CookieCsrfTokenRepository repository) { this.repository = repository; }
    public void invalidate(HttpServletRequest request, HttpServletResponse response) { repository.saveToken(null, request, response); }
}
