package vn.covie.auth.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class SessionManagementService {
    private final AuthSessionRepository sessions; private final AuthRefreshTokenRepository refreshTokens; private final AppClock clock;
    public SessionManagementService(AuthSessionRepository sessions, AuthRefreshTokenRepository refreshTokens, AppClock clock) { this.sessions=sessions; this.refreshTokens=refreshTokens; this.clock=clock; }
    @Transactional(readOnly = true)
    public AuthSessionEntity requireActiveSession(UUID sessionId) {
        return sessions.findUsableById(sessionId).filter(session -> session.isActiveAt(clock.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_SESSION_INVALID, HttpStatus.UNAUTHORIZED, "Authentication session is no longer active."));
    }
    @Transactional(readOnly = true)
    public List<AuthSessionEntity> activeSessions(UUID userId) {
        Instant now = clock.now();
        return sessions.findAllByUserIdOrderByCreatedAtDesc(userId).stream().filter(session -> session.isActiveAt(now))
                .sorted(Comparator.comparing(AuthSessionEntity::getLastUsedAt).reversed()).toList();
    }
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, AuthSessionRevocationReason reason) {
        AuthSessionEntity session = sessions.findByIdForUpdate(sessionId).orElseThrow(this::notFound);
        if (!session.getUserId().equals(userId)) throw notFound();
        revokeLocked(session, reason, clock.now());
    }
    @Transactional
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        Instant now = clock.now();
        sessions.findAllActiveByUserIdForUpdate(userId).stream().filter(session -> !session.getId().equals(currentSessionId))
                .forEach(session -> revokeLocked(session, AuthSessionRevocationReason.REVOKE_OTHERS, now));
    }
    @Transactional
    public void revokeForLogout(UUID sessionId) {
        sessions.findByIdForUpdate(sessionId).ifPresent(session -> revokeLocked(session, AuthSessionRevocationReason.LOGOUT, clock.now()));
    }
    private void revokeLocked(AuthSessionEntity session, AuthSessionRevocationReason reason, Instant now) {
        session.revoke(now, reason);
        List<AuthRefreshTokenEntity> tokens = refreshTokens.findAllBySessionIdForUpdate(session.getId());
        tokens.forEach(token -> { if (!token.isRevoked()) token.revoke(now); });
    }
    private ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Session not found"); }
}
