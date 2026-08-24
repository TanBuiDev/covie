package vn.covie.users.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserEntitySuspensionTest {
    @Test
    void suspendsOnlyActiveUserAndRestoresOnlySuspendedUser() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        UserEntity user = UserEntity.active(UUID.randomUUID(), "User", now);

        user.suspend(now.plusSeconds(1));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        user.restore(now.plusSeconds(2));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> user.restore(now.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
    }
}
