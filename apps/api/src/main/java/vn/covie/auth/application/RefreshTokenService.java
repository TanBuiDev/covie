package vn.covie.auth.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.ratelimit.RateLimitFingerprintService;
import vn.covie.ratelimit.RateLimitGroup;
import vn.covie.ratelimit.RedisTokenBucketRateLimiter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class RefreshTokenService {
    private final RefreshRotationTransaction rotation;
    private final AuthRefreshTokenRepository tokens;
    private final TokenFingerprint fingerprints;
    private RedisTokenBucketRateLimiter rateLimiter;
    private RateLimitFingerprintService rateLimitFingerprints;

    public RefreshTokenService(RefreshRotationTransaction rotation, AuthRefreshTokenRepository tokens, TokenFingerprint fingerprints) {
        this.rotation = rotation; this.tokens = tokens; this.fingerprints = fingerprints;
    }

    @Autowired(required = false)
    void configureRateLimiting(RedisTokenBucketRateLimiter limiter, RateLimitFingerprintService rateLimitFingerprints) {
        this.rateLimiter = limiter;
        this.rateLimitFingerprints = rateLimitFingerprints;
    }

    public IssuedSession refresh(String rawRefreshToken, String requestId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        if (rateLimiter != null && rateLimiter.enabled()) {
            rateLimiter.require(RateLimitGroup.AUTH_REFRESH_TOKEN, rateLimitFingerprints.opaqueCredential(rawRefreshToken));
        }
        RefreshRotationOutcome outcome = rotation.rotate(rawRefreshToken, requestId);
        if (outcome instanceof RefreshRotationOutcome.Rotated rotated) return rotated.session();
        throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED, HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
    }

    public Optional<UUID> sessionIdFor(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return Optional.empty();
        return tokens.findByTokenHash(fingerprints.sha256Hex(rawRefreshToken)).map(token -> token.getSessionId());
    }
}
