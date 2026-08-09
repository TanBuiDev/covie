package vn.covie.users.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;

class UserIdentityRepositoryTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Autowired
    UserRepository users;

    @Autowired
    UserIdentityRepository identities;

    @Test
    void googleSubjectIsCanonicalLookup() {
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), "Minh", NOW));
        identities.save(UserIdentityEntity.google(
                UUID.randomUUID(), user.getId(), "google-sub-456", "minh@example.com", NOW));

        assertThat(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub-456"))
                .get()
                .extracting(UserIdentityEntity::getUserId)
                .isEqualTo(user.getId());
    }

    @Test
    void localEmailIsUniqueWhileGoogleMayCarrySameEmailMetadata() {
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), "Minh", NOW));
        identities.save(UserIdentityEntity.google(
                UUID.randomUUID(), user.getId(), "google-sub-123", "minh@example.com", NOW));
        identities.save(UserIdentityEntity.email(
                UUID.randomUUID(), user.getId(), "minh@example.com", "{argon2id-v1}hash", NOW));

        assertThat(identities.findLocalEmail("minh@example.com")).isPresent();
    }
}
