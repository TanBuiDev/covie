package vn.covie.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter, boolean degraded) {
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, Math.max(0, remaining), Duration.ZERO, false);
    }

    public static RateLimitDecision failOpen() {
        return new RateLimitDecision(true, -1, Duration.ZERO, true);
    }
}
