package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.model.AuthRefreshTokenEntity;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.model.UserContactMethodEntity;
import vn.covie.users.model.UserEntity;

/** Direct PostgreSQL assertions protect partial unique indexes and persisted-token boundaries. */
class AuthDatabaseInvariantTest extends AuthAcceptanceSupport {
    @Test
    void databaseEnforcesIdentityDefaultAddressPrimaryContactAndRefreshHashUniqueness() {
        UserEntity first = activeUser("First invariant user");
        UserEntity second = activeUser("Second invariant user");
        String email = "same-" + UUID.randomUUID() + "@example.com";
        identities.saveAndFlush(vn.covie.users.model.UserIdentityEntity.email(UUID.randomUUID(), first.getId(), email, passwords.encode("Invariant-Password-42"), Instant.now()));
        assertThatThrownBy(() -> identities.saveAndFlush(vn.covie.users.model.UserIdentityEntity.email(UUID.randomUUID(), second.getId(), email, passwords.encode("Invariant-Password-43"), Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);

        addresses.saveAndFlush(UserAddressEntity.create(UUID.randomUUID(), first.getId(), "A", null, null, "One", 10, 106, PinSource.GPS, null, true, Instant.now()));
        assertThatThrownBy(() -> addresses.saveAndFlush(UserAddressEntity.create(UUID.randomUUID(), first.getId(), "B", null, null, "Two", 10, 106, PinSource.GPS, null, true, Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);
        contacts.saveAndFlush(UserContactMethodEntity.create(UUID.randomUUID(), first.getId(), ContactType.PHONE, "one", true, Instant.now()));
        assertThatThrownBy(() -> contacts.saveAndFlush(UserContactMethodEntity.create(UUID.randomUUID(), first.getId(), ContactType.PHONE, "two", true, Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);

        IssuedSession session = issuer.issue(first.getId(), device());
        String hash = fingerprints.sha256Hex(session.rawRefreshToken());
        assertThat(jdbc.queryForObject("select count(*) from auth_refresh_tokens where token_hash = ?", Integer.class, hash)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auth_refresh_tokens where token_hash = ?", Integer.class, session.rawRefreshToken())).isZero();
        assertThatThrownBy(() -> refreshTokens.saveAndFlush(AuthRefreshTokenEntity.create(UUID.randomUUID(), session.sessionId(), hash, session.refreshExpiresAt(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rawRefreshIsAbsentFromAllCharacterColumnsAndActionTicketIsSingleUse() {
        String subject = "invariant-google-" + UUID.randomUUID();
        String email = "invariant-" + UUID.randomUUID() + "@example.com";
        GoogleAuthResult.Onboarding issuedTicket = (GoogleAuthResult.Onboarding) google.handleLogin(claims(subject, email));
        String rawTicket = issuedTicket.ticket().rawToken();
        IssuedSession session = onboarding.completeOnboarding(rawTicket, "Single-Use-Password-42", "invariant-onboard");
        List<vn.covie.users.model.UserIdentityEntity> linked = identities.findAllByUserId(sessions.findById(session.sessionId()).orElseThrow().getUserId());
        assertThat(linked.stream().filter(identity -> identity.getProvider() == IdentityProvider.GOOGLE).map(vn.covie.users.model.UserIdentityEntity::getEmailNormalized))
                .containsExactly(email);
        assertThat(linked.stream().filter(identity -> identity.getProvider() == IdentityProvider.EMAIL).map(vn.covie.users.model.UserIdentityEntity::getEmailNormalized))
                .containsExactly(email);
        List<String> characterColumns = jdbc.query("select table_name || '.' || column_name from information_schema.columns where table_schema='public' and data_type in ('character varying','text','character')", (rs, row) -> rs.getString(1));
        long rawRefreshHits = characterColumns.stream().mapToLong(qualified -> {
            String[] parts = qualified.split("\\.", 2);
            return jdbc.queryForObject("select count(*) from \"" + parts[0] + "\" where \"" + parts[1] + "\"::text = ?", Long.class, session.rawRefreshToken());
        }).sum();

        assertThat(rawRefreshHits).isZero();
        assertThatThrownBy(() -> onboarding.completeOnboarding(rawTicket, "Single-Use-Password-42", "invariant-repeat"))
                .isInstanceOf(vn.covie.shared.web.ApiException.class)
                .extracting(error -> ((vn.covie.shared.web.ApiException) error).code())
                .isEqualTo(vn.covie.shared.web.ErrorCode.AUTH_ACTION_ALREADY_CONSUMED);
    }
}
