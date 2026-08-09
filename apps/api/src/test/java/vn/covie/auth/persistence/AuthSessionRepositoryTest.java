package vn.covie.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class AuthSessionRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    AuthSessionRepository sessions;

    @Test
    void activeSessionForActiveUserIsUsable() {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), "Minh", now));
        AuthSessionEntity session = sessions.save(AuthSessionEntity.create(
                UUID.randomUUID(), user.getId(), UUID.randomUUID(), now, now.plus(30, ChronoUnit.DAYS)));

        assertThat(sessions.findUsableById(session.getId())).isPresent();
    }
}
