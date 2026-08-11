package vn.covie.auth.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.auth.application.IssuedSession;

public record AuthSessionResponse(UUID sessionId, Instant expiresAt) {
    public static AuthSessionResponse from(IssuedSession session) { return new AuthSessionResponse(session.sessionId(), session.refreshExpiresAt()); }
}
