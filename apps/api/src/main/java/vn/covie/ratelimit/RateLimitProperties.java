package vn.covie.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.rate-limit")
public record RateLimitProperties(boolean enabled, String hmacKey, Map<RateLimitGroup, RateLimitPolicy> policies) {
    public RateLimitProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);
    }
}
