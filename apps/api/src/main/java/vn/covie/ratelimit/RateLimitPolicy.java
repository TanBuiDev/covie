package vn.covie.ratelimit;

import java.time.Duration;

public record RateLimitPolicy(int capacity, int refillTokens, Duration refillPeriod, boolean failClosed) {
    public RateLimitPolicy {
        if (capacity <= 0 || refillTokens <= 0 || refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("Rate-limit policy values must be positive.");
        }
    }
}
