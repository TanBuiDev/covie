package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.IssuedAuthActionTicket;
import vn.covie.auth.application.IssuedSession;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;

/** Fixture values are deliberately improbable so this catches accidental credential serialization. */
class AuthAuditSafetyTest extends AuthAcceptanceSupport {
    @Test
    void loginRefreshAndGoogleResetAuditsNeverContainSubmittedSecretsOrRawCredentials() {
        String oldPassword = "AUDIT-OLD-PASSWORD-89dfc7";
        String newPassword = "AUDIT-NEW-PASSWORD-6ab432";
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        String subject = "audit-google-" + UUID.randomUUID();
        UserEntity user = activeUser("Audit user");
        identities.save(UserIdentityEntity.google(UUID.randomUUID(), user.getId(), subject, email, Instant.now()));
        identities.save(UserIdentityEntity.email(UUID.randomUUID(), user.getId(), email, passwords.encode(oldPassword), Instant.now()));
        IssuedSession login = emailPasswords.login(email, oldPassword, device(), "audit-login");
        IssuedSession rotated = refresh.refresh(login.rawRefreshToken(), "audit-refresh");
        IssuedAuthActionTicket reset = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket();
        IssuedSession resetSession = recovery.resetWithGoogle(reset.rawToken(), newPassword, "audit-reset");

        String auditJson = jdbc.queryForObject("select coalesce(string_agg(coalesce(after_snapshot::text, ''), ' '), '') from audit_logs", String.class);
        assertThat(auditJson).doesNotContain(oldPassword, newPassword, login.accessToken().value(), login.rawRefreshToken(), rotated.rawRefreshToken(), reset.rawToken(), resetSession.accessToken().value(), "oauth-authorization-code-AUDIT");
    }
}
