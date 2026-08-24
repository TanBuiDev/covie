package vn.covie.ratelimit;

import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

public final class RateLimiterUnavailableException extends ApiException {
    public RateLimiterUnavailableException() {
        super(ErrorCode.RATE_LIMITER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter is temporarily unavailable.");
    }
}
