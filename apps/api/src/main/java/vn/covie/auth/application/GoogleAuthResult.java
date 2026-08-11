package vn.covie.auth.application;

import java.util.UUID;

public sealed interface GoogleAuthResult permits GoogleAuthResult.ExistingUser, GoogleAuthResult.Onboarding, GoogleAuthResult.PasswordReset {
    record ExistingUser(UUID userId, IssuedSession session) implements GoogleAuthResult {}
    record Onboarding(IssuedAuthActionTicket ticket) implements GoogleAuthResult {}
    record PasswordReset(IssuedAuthActionTicket ticket) implements GoogleAuthResult {}
}
