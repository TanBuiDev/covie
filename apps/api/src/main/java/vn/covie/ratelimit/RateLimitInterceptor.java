package vn.covie.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public final class RateLimitInterceptor implements HandlerInterceptor {
    private final RedisTokenBucketRateLimiter limiter;
    private final RateLimitFingerprintService fingerprints;
    private final ClientIpResolver ips;

    public RateLimitInterceptor(RedisTokenBucketRateLimiter limiter, RateLimitFingerprintService fingerprints, ClientIpResolver ips) {
        this.limiter = limiter;
        this.fingerprints = fingerprints;
        this.ips = ips;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!limiter.enabled()) return true;
        RateLimitGroup group = resolveGroup(request, handler);
        if (group == null) return true;
        limiter.require(group, key(group, request));
        return true;
    }

    private RateLimitGroup resolveGroup(HttpServletRequest request, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/v1/admin/")) return RateLimitGroup.ADMIN;
        if ("POST".equals(method) && "/api/v1/auth/login".equals(path)) return RateLimitGroup.AUTH_LOGIN_IP;
        if ("POST".equals(method) && "/api/v1/auth/refresh".equals(path)) return RateLimitGroup.AUTH_REFRESH_IP;
        if ("GET".equals(method) && "/api/v1/auth/google/start".equals(path)) return RateLimitGroup.AUTH_GOOGLE_START;
        if ("GET".equals(method) && (path.startsWith("/api/v1/catalog") || path.startsWith("/api/v1/categories") || path.startsWith("/api/v1/location-areas"))) return RateLimitGroup.PUBLIC_DISCOVERY;
        if ("POST".equals(method) && path.startsWith("/api/v1/checkouts")) return RateLimitGroup.CHECKOUT;
        if ("POST".equals(method) && path.contains("/orders/")) return RateLimitGroup.ORDER_COMMAND;
        if ("POST".equals(method) && "/api/v1/me/need-requests".equals(path)) return RateLimitGroup.NEED_CREATE;
        if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && path.contains("need-respons")) return RateLimitGroup.NEED_RESPONSE;
        if ("POST".equals(method) && path.startsWith("/api/v1/reports")) return RateLimitGroup.REPORT;
        if ("POST".equals(method) && path.startsWith("/api/v1/media/uploads")) return RateLimitGroup.MEDIA_UPLOAD;
        if (handler instanceof HandlerMethod methodHandler) {
            RateLimited annotation = findAnnotation(methodHandler.getMethod());
            if (annotation == null) annotation = findAnnotation(methodHandler.getBeanType());
            return annotation == null ? null : annotation.value();
        }
        return null;
    }

    private String key(RateLimitGroup group, HttpServletRequest request) {
        if (group == RateLimitGroup.AUTH_LOGIN_IP || group == RateLimitGroup.AUTH_REFRESH_IP || group == RateLimitGroup.AUTH_GOOGLE_START || group == RateLimitGroup.PUBLIC_DISCOVERY) {
            return fingerprints.ip(ips.resolve(request));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return fingerprints.ip(ips.resolve(request));
        }
        String userKey = auth.getName();
        if (group == RateLimitGroup.NEED_RESPONSE) {
            Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (raw instanceof Map<?, ?> variables) {
                Object merchantId = variables.get("merchantId");
                if (merchantId == null) merchantId = variables.get("merchantProfileId");
                if (merchantId != null) userKey += ":" + merchantId;
            }
        }
        return userKey;
    }

    private static RateLimited findAnnotation(AnnotatedElement element) {
        return AnnotatedElementUtils.findMergedAnnotation(element, RateLimited.class);
    }
}
