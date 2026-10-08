package vn.covie.ads;

public final class AdRules {
    private AdRules() {}
    public static void validateTarget(String target) {
        try {
            if (target == null || target.contains("\\") || target.chars().anyMatch(c -> c <= 32 || c == 127)) throw new IllegalArgumentException();
            var uri = java.net.URI.create(target);
            if (target.startsWith("/") && !target.startsWith("//") && uri.getAuthority() == null) return;
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw invalid("Advertising target must be a local path or an HTTPS URL."); }
    }
    public static boolean acceptsEvent(String type, long visibleMillis) {
        return "CLICK".equals(type) || ("IMPRESSION".equals(type) && visibleMillis >= 1000);
    }
    public static vn.covie.shared.web.ApiException invalid(String message) {
        return new vn.covie.shared.web.ApiException(vn.covie.shared.web.ErrorCode.VALIDATION_FAILED, org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
