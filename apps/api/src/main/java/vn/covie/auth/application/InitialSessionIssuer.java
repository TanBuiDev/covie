package vn.covie.auth.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.security.AuthProperties;
import vn.covie.auth.security.IssuedAccessToken;
import vn.covie.auth.security.JwtTokenService;
import vn.covie.auth.token.OpaqueTokenGenerator;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;

@Service
public class InitialSessionIssuer {
    private final AuthSessionRepository sessions;
    private final AuthRefreshTokenRepository refreshTokens;
    private final SystemRoleAssignmentRepository roleAssignments;
    private final OpaqueTokenGenerator tokens;
    private final TokenFingerprint fingerprints;
    private final JwtTokenService jwtTokens;
    private final AuthProperties properties;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public InitialSessionIssuer(AuthSessionRepository sessions, AuthRefreshTokenRepository refreshTokens,
            SystemRoleAssignmentRepository roleAssignments, OpaqueTokenGenerator tokens, TokenFingerprint fingerprints,
            JwtTokenService jwtTokens, AuthProperties properties, UuidV7Generator ids, AppClock clock) {
        this.sessions = sessions; this.refreshTokens = refreshTokens; this.roleAssignments = roleAssignments;
        this.tokens = tokens; this.fingerprints = fingerprints; this.jwtTokens = jwtTokens; this.properties = properties;
        this.ids = ids; this.clock = clock;
    }

    @Transactional
    public IssuedSession issue(UUID userId, DeviceMetadata device) {
        Instant now = clock.now();
        Instant sessionExpiresAt = now.plus(properties.refreshTokenTtl());
        UUID sessionId = ids.generate();
        AuthSessionEntity session = AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), device, now, sessionExpiresAt);
        sessions.save(session);
        String rawRefreshToken = tokens.generate256BitToken();
        refreshTokens.save(AuthRefreshTokenEntity.create(ids.generate(), sessionId, fingerprints.sha256Hex(rawRefreshToken), sessionExpiresAt, now));
        Set<SystemRole> roles = roleAssignments.findAllByUserId(userId).stream().map(assignment -> assignment.getRole()).collect(Collectors.toUnmodifiableSet());
        IssuedAccessToken accessToken = jwtTokens.issueAccessToken(userId, sessionId, roles);
        return new IssuedSession(sessionId, rawRefreshToken, sessionExpiresAt, accessToken);
    }
}
