package vn.covie.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CurrentActorTest {
    @Test
    void defensivelyCopiesGlobalRoles() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        HashSet<SystemRole> roles = new HashSet<>();
        roles.add(SystemRole.USER);
        CurrentActor actor = new CurrentActor(userId, roles, sessionId);

        roles.add(SystemRole.ADMIN);

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.globalRoles()).containsExactly(SystemRole.USER);
        assertThat(actor.sessionId()).isEqualTo(sessionId);
    }
}
