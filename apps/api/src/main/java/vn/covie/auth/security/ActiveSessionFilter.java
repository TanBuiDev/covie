package vn.covie.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.shared.web.ApiException;

@Component
public final class ActiveSessionFilter extends OncePerRequestFilter {
    private final SessionManagementService sessions; private final ApiAuthenticationEntryPoint entryPoint;
    public ActiveSessionFilter(SessionManagementService sessions, ApiAuthenticationEntryPoint entryPoint) { this.sessions=sessions; this.entryPoint=entryPoint; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                UUID sessionId=UUID.fromString(jwt.getClaimAsString("sessionId")); UUID userId=UUID.fromString(jwt.getSubject());
                sessions.requireActiveSessionForUser(sessionId, userId);
            } catch (ApiException | IllegalArgumentException exception) { SecurityContextHolder.clearContext(); entryPoint.commence(request,response,new org.springframework.security.core.AuthenticationException("Invalid session") {}); return; }
        }
        chain.doFilter(request,response);
    }
}
