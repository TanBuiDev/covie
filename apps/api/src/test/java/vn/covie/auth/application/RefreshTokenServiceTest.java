package vn.covie.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

class RefreshTokenServiceTest extends PostgresIntegrationTest {
    @Autowired RefreshTokenService service;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthRefreshTokenRepository refreshTokens;
    @Autowired TokenFingerprint fingerprints;
    @Autowired UserRepository users;
    @Autowired SystemRoleAssignmentRepository roles;

    @Test
    void rotatesRefreshTokenWithoutSlidingSessionExpiry() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        users.save(UserEntity.active(userId, "Minh", now));
        roles.save(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), userId, SystemRole.USER, now));
        Instant expiresAt = now.plus(30, ChronoUnit.DAYS);
        AuthSessionEntity session = sessions.save(AuthSessionEntity.create(UUID.randomUUID(), userId, UUID.randomUUID(), now, expiresAt));
        String r1 = "refresh-r1-" + UUID.randomUUID();
        AuthRefreshTokenEntity token = refreshTokens.save(AuthRefreshTokenEntity.create(UUID.randomUUID(), session.getId(), fingerprints.sha256Hex(r1), expiresAt, now));

        IssuedSession rotated = service.refresh(r1, "req-refresh");

        AuthRefreshTokenEntity consumed = refreshTokens.findById(token.getId()).orElseThrow();
        assertThat(consumed.getUsedAt()).isNotNull();
        assertThat(consumed.getReplacedByTokenId()).isNotNull();
        AuthRefreshTokenEntity successor = refreshTokens.findById(consumed.getReplacedByTokenId()).orElseThrow();
        assertThat(successor.getTokenHash()).isNotEqualTo(fingerprints.sha256Hex(r1));
        Instant persistedExpiry = sessions.findById(session.getId()).orElseThrow().getExpiresAt();
        assertThat(successor.getExpiresAt()).isEqualTo(persistedExpiry);
        assertThat(rotated.sessionId()).isEqualTo(session.getId());
        assertThat(persistedExpiry).isEqualTo(sessions.findById(session.getId()).orElseThrow().getExpiresAt());
    }

    @Test
    void detectsReuseAndRevokesEntireRefreshFamily() {
        Instant now = Instant.now(); UUID userId = UUID.randomUUID();
        users.save(UserEntity.active(userId, "Minh", now)); roles.save(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), userId, SystemRole.USER, now));
        AuthSessionEntity session = sessions.save(AuthSessionEntity.create(UUID.randomUUID(), userId, UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));
        String r1 = "refresh-r1-" + UUID.randomUUID();
        refreshTokens.save(AuthRefreshTokenEntity.create(UUID.randomUUID(), session.getId(), fingerprints.sha256Hex(r1), session.getExpiresAt(), now));
        service.refresh(r1, "req-first");

        assertThatThrownBy(() -> service.refresh(r1, "req-reuse"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        assertThat(sessions.findById(session.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokens.findAllBySessionId(session.getId())).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }
}
