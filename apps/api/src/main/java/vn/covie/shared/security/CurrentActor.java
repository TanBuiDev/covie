package vn.covie.shared.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CurrentActor(UUID userId, Set<SystemRole> globalRoles, UUID sessionId) {
    public CurrentActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(globalRoles, "globalRoles");
        Objects.requireNonNull(sessionId, "sessionId");
        globalRoles = Set.copyOf(globalRoles);
    }
}
