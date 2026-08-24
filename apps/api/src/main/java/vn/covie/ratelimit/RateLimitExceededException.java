package vn.covie.ratelimit;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

public final class RateLimitExceededException extends ApiException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "Too many requests.");
        long millis = retryAfter == null ? 0 : retryAfter.toMillis();
        this.retryAfterSeconds = Math.max(1, (millis + 999) / 1000);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
