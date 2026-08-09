package vn.covie.auth.application;

import java.util.UUID;

public sealed interface RefreshRotationOutcome permits RefreshRotationOutcome.Rotated, RefreshRotationOutcome.ReuseDetected {
    record Rotated(IssuedSession session) implements RefreshRotationOutcome {}
    record ReuseDetected(UUID sessionId) implements RefreshRotationOutcome {}
}
