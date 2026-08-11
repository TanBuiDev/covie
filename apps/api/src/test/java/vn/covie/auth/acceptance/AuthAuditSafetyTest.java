package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        String csrf = "AUDIT-CSRF-TOKEN-c91e38";
        String oauthCode = "AUDIT-OAUTH-AUTHORIZATION-CODE-6c7bf2";
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        String subject = "audit-google-" + UUID.randomUUID();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); root.addAppender(logs); logs.start();
        IssuedSession login = null; IssuedSession rotated = null; IssuedAuthActionTicket reset = null; IssuedSession resetSession = null;
        try {
            UserEntity user = activeUser("Audit user");
            identities.save(UserIdentityEntity.google(UUID.randomUUID(), user.getId(), subject, email, Instant.now()));
            identities.save(UserIdentityEntity.email(UUID.randomUUID(), user.getId(), email, passwords.encode(oldPassword), Instant.now()));
            login = emailPasswords.login(email, oldPassword, device(), "audit-login");
            rotated = refresh.refresh(login.rawRefreshToken(), "audit-refresh");
            reset = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket();
            resetSession = recovery.resetWithGoogle(reset.rawToken(), newPassword, "audit-reset");
        } finally { logs.stop(); root.detachAppender(logs); }

        String auditJson = jdbc.queryForObject("select coalesce(string_agg(coalesce(after_snapshot::text, ''), ' '), '') from audit_logs", String.class);
        String capturedLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(auditJson + "\n" + capturedLogs).doesNotContain(oldPassword, newPassword, login.accessToken().value(), login.rawRefreshToken(), rotated.rawRefreshToken(), reset.rawToken(), resetSession.accessToken().value(), csrf, oauthCode);
    }
}
