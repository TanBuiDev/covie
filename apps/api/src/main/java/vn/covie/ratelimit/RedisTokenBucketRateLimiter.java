package vn.covie.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class RedisTokenBucketRateLimiter {
    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<String> script;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("ratelimit/token-bucket.lua"));
        this.script.setResultType(String.class);
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public RateLimitDecision require(RateLimitGroup group, String key) {
        if (!properties.enabled()) return RateLimitDecision.allowed(Long.MAX_VALUE);
        RateLimitPolicy policy = properties.policies().get(group);
        if (policy == null) throw new IllegalStateException("Missing rate-limit policy: " + group);
        String redisKey = "covie:rl:" + group.name() + ":" + key;
        try {
            String value = redis.execute(script, List.of(redisKey),
                    Long.toString(System.currentTimeMillis()),
                    Integer.toString(policy.capacity()),
                    Integer.toString(policy.refillTokens()),
                    Long.toString(policy.refillPeriod().toMillis()));
            if (value == null) throw new IllegalStateException("Rate limiter returned no decision.");
            String[] parts = value.split(":", -1);
            if (parts.length != 3) throw new IllegalStateException("Rate limiter returned malformed decision.");
            boolean allowed = "1".equals(parts[0]);
            long remaining = Long.parseLong(parts[1]);
            Duration retryAfter = Duration.ofMillis(Math.max(0, Long.parseLong(parts[2])));
            if (!allowed) throw new RateLimitExceededException(retryAfter);
            return RateLimitDecision.allowed(remaining);
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (policy.failClosed()) throw new RateLimiterUnavailableException();
            return RateLimitDecision.failOpen();
        }
    }
}
