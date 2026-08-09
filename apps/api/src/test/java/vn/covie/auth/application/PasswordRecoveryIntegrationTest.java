package vn.covie.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthActionTicketEntity;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class PasswordRecoveryIntegrationTest extends PostgresIntegrationTest {
    @Autowired PasswordRecoveryService service;
    @Autowired AuthActionTicketRepository tickets;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthRefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired TokenFingerprint fingerprints;
    @Autowired PasswordEncoder passwords;

    @Test
    void resetsPasswordAndRevokesAllExistingSessionsBeforeIssuingNewOne() {
        Instant now = Instant.now(); UUID userId = UUID.randomUUID();
        users.save(UserEntity.active(userId, "Minh", now));
        identities.save(UserIdentityEntity.google(UUID.randomUUID(), userId, "google-recovery-" + UUID.randomUUID(), "minh@example.com", now));
        UserIdentityEntity email = identities.save(UserIdentityEntity.email(UUID.randomUUID(), userId, "recovery-" + UUID.randomUUID() + "@example.com", passwords.encode("old password here"), now));
        AuthSessionEntity oldOne = sessions.save(AuthSessionEntity.create(UUID.randomUUID(), userId, UUID.randomUUID(), now, now.plusSeconds(3600)));
        AuthSessionEntity oldTwo = sessions.save(AuthSessionEntity.create(UUID.randomUUID(), userId, UUID.randomUUID(), now, now.plusSeconds(3600)));
        AuthRefreshTokenEntity oldOneToken = refreshTokens.save(AuthRefreshTokenEntity.create(UUID.randomUUID(), oldOne.getId(), fingerprints.sha256Hex("old-one-" + UUID.randomUUID()), now.plusSeconds(3600), now));
        AuthRefreshTokenEntity oldTwoToken = refreshTokens.save(AuthRefreshTokenEntity.create(UUID.randomUUID(), oldTwo.getId(), fingerprints.sha256Hex("old-two-" + UUID.randomUUID()), now.plusSeconds(3600), now));
        String raw = "reset-ticket-" + UUID.randomUUID();
        AuthActionTicketEntity ticket = tickets.save(AuthActionTicketEntity.create(UUID.randomUUID(), AuthActionPurpose.PASSWORD_RESET, fingerprints.sha256Hex(raw), userId,
                identities.findAllByUserId(userId).stream().filter(value -> value.getProvider().name().equals("GOOGLE")).findFirst().orElseThrow().getProviderSubject(), email.getEmailNormalized(), true, "Minh", now, now.plusSeconds(600)));

        IssuedSession issued = service.resetWithGoogle(raw, "new password here", "req-reset");

        assertThat(passwords.matches("old password here", identities.findLocalEmail(email.getEmailNormalized()).orElseThrow().getPasswordHash())).isFalse();
        assertThat(passwords.matches("new password here", identities.findLocalEmail(email.getEmailNormalized()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(sessions.findById(oldOne.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessions.findById(oldTwo.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokens.findById(oldOneToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokens.findById(oldTwoToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(issued.sessionId()).isNotEqualTo(oldOne.getId()).isNotEqualTo(oldTwo.getId());
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void rejectsActionTicketsForAnyOtherPurpose() {
        Instant now = Instant.now(); UUID userId = UUID.randomUUID(); String raw = "wrong-purpose-" + UUID.randomUUID();
        users.save(UserEntity.active(userId, "Minh", now));
        tickets.save(AuthActionTicketEntity.create(UUID.randomUUID(), AuthActionPurpose.ONBOARDING, fingerprints.sha256Hex(raw), userId,
                "google-" + UUID.randomUUID(), "minh@example.com", true, "Minh", now, now.plusSeconds(600)));

        assertThatThrownBy(() -> service.resetWithGoogle(raw, "correct horse battery staple", "req-reset"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.AUTH_ACTION_PURPOSE_MISMATCH);
    }
}
