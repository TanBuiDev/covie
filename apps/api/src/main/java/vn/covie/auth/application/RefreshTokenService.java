package vn.covie.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class RefreshTokenService {
    private final RefreshRotationTransaction rotation;
    private final AuthRefreshTokenRepository tokens;
    private final TokenFingerprint fingerprints;
    public RefreshTokenService(RefreshRotationTransaction rotation, AuthRefreshTokenRepository tokens, TokenFingerprint fingerprints) { this.rotation = rotation; this.tokens = tokens; this.fingerprints = fingerprints; }
    public IssuedSession refresh(String rawRefreshToken, String requestId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        RefreshRotationOutcome outcome = rotation.rotate(rawRefreshToken, requestId);
        if (outcome instanceof RefreshRotationOutcome.Rotated rotated) return rotated.session();
        throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED, HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
    }
    public Optional<UUID> sessionIdFor(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return Optional.empty();
        return tokens.findByTokenHash(fingerprints.sha256Hex(rawRefreshToken)).map(token -> token.getSessionId());
    }
}
