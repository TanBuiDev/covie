package vn.covie.auth.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.auth.model.AuthSessionEntity;

public record AuthSessionSummaryResponse(UUID id, String deviceName, boolean current, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
    public static AuthSessionSummaryResponse from(AuthSessionEntity session, UUID currentSessionId) {
        return new AuthSessionSummaryResponse(session.getId(), session.getDeviceName(), session.getId().equals(currentSessionId), session.getCreatedAt(), session.getLastUsedAt(), session.getExpiresAt());
    }
}
