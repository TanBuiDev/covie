package vn.covie.auth.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.security.IssuedAccessToken;
import vn.covie.auth.security.JwtTokenService;
import vn.covie.auth.token.OpaqueTokenGenerator;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;

@Service
public class RefreshRotationTransaction {
    private final AuthRefreshTokenRepository tokens; private final AuthSessionRepository sessions;
    private final TokenFingerprint fingerprints; private final OpaqueTokenGenerator generator; private final UuidV7Generator ids;
    private final AppClock clock; private final JwtTokenService jwt; private final SystemRoleAssignmentRepository roles; private final AuditLogWriter audit;
    public RefreshRotationTransaction(AuthRefreshTokenRepository tokens, AuthSessionRepository sessions, TokenFingerprint fingerprints,
            OpaqueTokenGenerator generator, UuidV7Generator ids, AppClock clock, JwtTokenService jwt,
            SystemRoleAssignmentRepository roles, AuditLogWriter audit) {
        this.tokens=tokens; this.sessions=sessions; this.fingerprints=fingerprints; this.generator=generator; this.ids=ids; this.clock=clock; this.jwt=jwt; this.roles=roles; this.audit=audit;
    }
    @Transactional
    public RefreshRotationOutcome rotate(String rawRefreshToken, String requestId) {
        AuthRefreshTokenEntity candidate = tokens.findByTokenHashForUpdate(fingerprints.sha256Hex(rawRefreshToken)).orElseThrow(this::invalid);
        AuthSessionEntity session = sessions.findByIdForUpdate(candidate.getSessionId()).orElseThrow(this::invalid);
        AuthRefreshTokenEntity token = candidate;
        Instant now = clock.now();
        if (token.isUsedOrReplaced()) { revokeFamily(session, now, requestId); return new RefreshRotationOutcome.ReuseDetected(session.getId()); }
        if (!session.isActiveAt(now) || token.isRevoked() || !token.getExpiresAt().isAfter(now)) throw invalid();
        NewRefresh successor = createSuccessor(session, now);
        token.replaceWith(successor.id(), now); session.markUsed(now);
        Set<SystemRole> assigned = roles.findAllByUserId(session.getUserId()).stream().map(value -> value.getRole()).collect(Collectors.toUnmodifiableSet());
        IssuedAccessToken access = jwt.issueAccessToken(session.getUserId(), session.getId(), assigned);
        return new RefreshRotationOutcome.Rotated(new IssuedSession(session.getId(), successor.rawToken(), successor.expiresAt(), access));
    }
    private NewRefresh createSuccessor(AuthSessionEntity session, Instant now) {
        UUID id = ids.generate(); String raw = generator.generate256BitToken();
        tokens.save(AuthRefreshTokenEntity.create(id, session.getId(), fingerprints.sha256Hex(raw), session.getExpiresAt(), now));
        return new NewRefresh(id, raw, session.getExpiresAt());
    }
    private void revokeFamily(AuthSessionEntity session, Instant now, String requestId) {
        session.revoke(now, AuthSessionRevocationReason.REFRESH_REUSE);
        tokens.findAllBySessionIdForUpdate(session.getId()).forEach(token -> { if (!token.isRevoked()) token.revoke(now); });
        audit.securityEvent(session.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "AUTH_SESSION", session.getId(), requestId, Map.of());
    }
    private ApiException invalid() { return new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Refresh token is invalid"); }
    private record NewRefresh(UUID id, String rawToken, Instant expiresAt) {}
}
