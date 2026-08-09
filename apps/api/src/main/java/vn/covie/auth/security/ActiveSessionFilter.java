package vn.covie.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.shared.time.AppClock;

@Component
public final class ActiveSessionFilter extends OncePerRequestFilter {
    private final AuthSessionRepository sessions; private final AppClock clock; private final ApiAuthenticationEntryPoint entryPoint;
    public ActiveSessionFilter(AuthSessionRepository sessions, AppClock clock, ApiAuthenticationEntryPoint entryPoint) { this.sessions=sessions; this.clock=clock; this.entryPoint=entryPoint; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                UUID sessionId=UUID.fromString(jwt.getClaimAsString("sessionId")); UUID userId=UUID.fromString(jwt.getSubject()); Instant now=clock.now();
                var session=sessions.findUsableById(sessionId).filter(value -> value.getUserId().equals(userId)).filter(value -> value.isActiveAt(now));
                if (session.isEmpty()) { SecurityContextHolder.clearContext(); entryPoint.commence(request,response,new org.springframework.security.core.AuthenticationException("Inactive session") {}); return; }
                sessions.touchLastUsedIfBefore(sessionId, now, now.minus(Duration.ofMinutes(5)));
            } catch (RuntimeException exception) { SecurityContextHolder.clearContext(); entryPoint.commence(request,response,new org.springframework.security.core.AuthenticationException("Invalid session") {}); return; }
        }
        chain.doFilter(request,response);
    }
}
