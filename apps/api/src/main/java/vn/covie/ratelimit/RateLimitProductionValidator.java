package vn.covie.ratelimit;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public final class RateLimitProductionValidator implements ApplicationRunner {
    private final RateLimitProperties properties;

    public RateLimitProductionValidator(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        require(properties.enabled(), "covie.rate-limit.enabled");
        String key = properties.hmacKey();
        int bytes = key == null ? 0 : key.getBytes(StandardCharsets.UTF_8).length;
        require(bytes >= 32, "covie.rate-limit.hmac-key");
        require(properties.policies().keySet().containsAll(EnumSet.allOf(RateLimitGroup.class)), "covie.rate-limit.policies");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("Unsafe production configuration: " + name);
    }
}
