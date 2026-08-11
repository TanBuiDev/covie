package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.covie.audit.AuditLogRepository;
import vn.covie.auth.application.AuthApplicationService;
import vn.covie.auth.application.EmailPasswordLoginService;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.GoogleAuthService;
import vn.covie.auth.application.GoogleClaims;
import vn.covie.auth.application.IssuedAuthActionTicket;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.application.PasswordRecoveryService;
import vn.covie.auth.application.RefreshTokenService;
import vn.covie.auth.application.SessionManagementService;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;

/**
 * Real PostGIS/PostgreSQL acceptance coverage.  Google claims are injected at the
 * success-handler boundary; neither these tests nor the tested services mock a repository.
 */
class AuthAcceptanceTest extends AuthAcceptanceSupport {

    @Test
    void firstGoogleRegistrationCreatesOneActiveUserBothIdentitiesRoleSessionAndUsableJwt() {
        String subject = "google-first-" + UUID.randomUUID();
        String email = "first-" + UUID.randomUUID() + "@example.com";

        GoogleAuthResult.Onboarding start = (GoogleAuthResult.Onboarding) google.handleLogin(claims(subject, email));
        IssuedSession issued = onboarding.completeOnboarding(start.ticket().rawToken(), "Password-First-Secret-42", "first-registration");

        UserIdentityEntity googleIdentity = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).orElseThrow();
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById(googleIdentity.getUserId()).orElseThrow().isActive()).isTrue();
        assertThat(identities.findAllByUserId(googleIdentity.getUserId())).extracting(UserIdentityEntity::getProvider)
                .containsExactlyInAnyOrder(IdentityProvider.GOOGLE, IdentityProvider.EMAIL);
        assertThat(passwords.matches("Password-First-Secret-42", identities.findLocalEmail(email).orElseThrow().getPasswordHash())).isTrue();
        assertThat(roles.findAllByUserId(googleIdentity.getUserId())).extracting(SystemRoleAssignmentEntity::getRole).containsExactly(SystemRole.USER);
        assertThat(sessions.findById(issued.sessionId())).isPresent();
        assertThat(refreshTokens.findAllBySessionId(issued.sessionId())).hasSize(1);
        assertThat(jwt.issueAccessToken(googleIdentity.getUserId(), issued.sessionId(), java.util.Set.of(SystemRole.USER)).value()).isNotBlank();
        assertThat(sessionManagement.requireActiveSession(issued.sessionId()).getUserId()).isEqualTo(googleIdentity.getUserId());
    }

    @Test
    void returningGoogleAndEmailLoginsResolveTheOriginalUserWithoutDuplicateIdentities() {
        String subject = "google-returning-" + UUID.randomUUID();
        String email = "returning-" + UUID.randomUUID() + "@example.com";
        GoogleAuthResult.Onboarding start = (GoogleAuthResult.Onboarding) google.handleLogin(claims(subject, email));
        IssuedSession initial = onboarding.completeOnboarding(start.ticket().rawToken(), "Password-Returning-42", "returning-onboard");

        GoogleAuthResult.ExistingUser googleLogin = (GoogleAuthResult.ExistingUser) google.handleLogin(claims(subject, email));
        IssuedSession emailLogin = emailPasswords.login(email.toUpperCase(), "Password-Returning-42", device(), "returning-email");

        assertThat(googleLogin.userId()).isEqualTo(sessions.findById(initial.sessionId()).orElseThrow().getUserId());
        assertThat(sessions.findById(emailLogin.sessionId()).orElseThrow().getUserId()).isEqualTo(googleLogin.userId());
        assertThat(users.count()).isEqualTo(1);
        assertThat(identities.count()).isEqualTo(2);
    }

    @Test
    void GoogleRecoveryRevokesOldSessionsRejectsOldPasswordAndIssuesUsableNewSession() {
        UserEntity user = activeUser("Recovery user");
        String subject = "google-recovery-" + UUID.randomUUID();
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        identities.save(UserIdentityEntity.google(UUID.randomUUID(), user.getId(), subject, email, Instant.now()));
        identities.save(UserIdentityEntity.email(UUID.randomUUID(), user.getId(), email, passwords.encode("Old-Password-42"), Instant.now()));
        IssuedSession old = emailPasswords.login(email, "Old-Password-42", device(), "old-login");
        IssuedAuthActionTicket resetTicket = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket();

        IssuedSession replacement = recovery.resetWithGoogle(resetTicket.rawToken(), "New-Password-42", "google-recovery");

        assertThat(sessions.findById(old.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> emailPasswords.login(email, "Old-Password-42", device(), "old-password"))
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(emailPasswords.login(email, "New-Password-42", device(), "new-password").sessionId()).isNotEqualTo(old.sessionId());
        assertThat(sessionManagement.requireActiveSession(replacement.sessionId()).getUserId()).isEqualTo(user.getId());
        assertThat(tickets.findByTokenHash(fingerprints.sha256Hex(resetTicket.rawToken())).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void refreshRotatesWithoutExtendingExpiryAndReuseRevokesOnlyThatSessionFamily() {
        UserEntity user = activeUser("Refresh user");
        IssuedSession firstDevice = issuer.issue(user.getId(), device());
        IssuedSession secondDevice = issuer.issue(user.getId(), device());
        Instant fixedSessionExpiry = sessions.findById(firstDevice.sessionId()).orElseThrow().getExpiresAt();

        IssuedSession rotated = refresh.refresh(firstDevice.rawRefreshToken(), "refresh-r1");

        assertThat(rotated.sessionId()).isEqualTo(firstDevice.sessionId());
        assertThat(sessions.findById(rotated.sessionId()).orElseThrow().getExpiresAt()).isEqualTo(fixedSessionExpiry);
        assertThat(rotated.refreshExpiresAt()).isEqualTo(sessions.findById(rotated.sessionId()).orElseThrow().getExpiresAt());
        assertThatThrownBy(() -> refresh.refresh(firstDevice.rawRefreshToken(), "refresh-reuse"))
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        assertThat(sessions.findById(firstDevice.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessions.findById(secondDevice.sessionId()).orElseThrow().getRevokedAt()).isNull();
        assertThatThrownBy(() -> refresh.refresh(rotated.rawRefreshToken(), "refresh-after-revoke"))
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void simultaneousUsesOfOneRefreshTokenSerializeOnPostgresAndDetectReuse() throws Exception {
        UserEntity user = activeUser("Concurrent refresh user");
        IssuedSession initial = issuer.issue(user.getId(), device());
        CyclicBarrier ready = new CyclicBarrier(2);
        Callable<ErrorCode> attempt = () -> {
            ready.await();
            try { refresh.refresh(initial.rawRefreshToken(), "concurrent-refresh"); return null; }
            catch (ApiException exception) { return exception.code(); }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> left = pool.submit(attempt);
            Future<ErrorCode> right = pool.submit(attempt);
            assertThat(java.util.Arrays.asList(left.get(10, java.util.concurrent.TimeUnit.SECONDS), right.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(null, ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        } finally { pool.shutdownNow(); }
        assertThat(sessions.findById(initial.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokens.findAllBySessionId(initial.sessionId())).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void refreshRacingLogoutOrGoogleResetCompletesAndLeavesTheOriginalFamilyRevoked() throws Exception {
        UserEntity logoutUser = activeUser("Refresh logout race");
        IssuedSession logoutSession = issuer.issue(logoutUser.getId(), device());
        race(() -> refreshOutcome(logoutSession.rawRefreshToken(), "race-refresh-logout"),
                () -> { sessionManagement.revokeForLogout(logoutSession.sessionId()); return null; });
        assertRevokedFamily(logoutSession.sessionId());

        UserEntity resetUser = activeUser("Refresh reset race");
        String subject = "refresh-reset-" + UUID.randomUUID();
        String email = "refresh-reset-" + UUID.randomUUID() + "@example.com";
        identities.save(UserIdentityEntity.google(UUID.randomUUID(), resetUser.getId(), subject, email, Instant.now()));
        identities.save(UserIdentityEntity.email(UUID.randomUUID(), resetUser.getId(), email, passwords.encode("Reset-Race-Password-42"), Instant.now()));
        IssuedSession resetSession = issuer.issue(resetUser.getId(), device());
        String ticket = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket().rawToken();
        race(() -> refreshOutcome(resetSession.rawRefreshToken(), "race-refresh-reset"),
                () -> { recovery.resetWithGoogle(ticket, "Reset-Race-New-Password-42", "race-reset"); return null; });
        assertRevokedFamily(resetSession.sessionId());
    }

    @Test
    void oldReuseRacingSuccessorRefreshCommitsReuseAuditAndRevokesTheWholeFamily() throws Exception {
        UserEntity user = activeUser("Reuse successor race");
        IssuedSession first = issuer.issue(user.getId(), device());
        IssuedSession successor = refresh.refresh(first.rawRefreshToken(), "rotate-before-race");

        java.util.List<ErrorCode> outcomes = race(
                () -> refreshOutcome(first.rawRefreshToken(), "old-reuse-race"),
                () -> refreshOutcome(successor.rawRefreshToken(), "successor-race"));

        assertThat(outcomes).contains(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        assertRevokedFamily(first.sessionId());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REFRESH_TOKEN_REUSE_DETECTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshReuseWithMaximumAcceptedRequestIdPersistsAuditAndFamilyRevocation() {
        UserEntity user = activeUser("Maximum request id");
        IssuedSession issued = issuer.issue(user.getId(), device());
        refresh.refresh(issued.rawRefreshToken(), "rotate-maximum-request-id");
        String requestId = "r".repeat(100);

        assertThatThrownBy(() -> refresh.refresh(issued.rawRefreshToken(), requestId))
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        assertRevokedFamily(issued.sessionId());
        assertThat(jdbc.queryForObject("select request_id from audit_logs where action = 'REFRESH_TOKEN_REUSE_DETECTED'", String.class)).isEqualTo(requestId);
    }

    @Test
    void revokeOneAndRevokeOthersPreserveTheRequestedCurrentSession() {
        UserEntity user = activeUser("Session management user");
        IssuedSession current = issuer.issue(user.getId(), device());
        IssuedSession another = issuer.issue(user.getId(), device());
        IssuedSession third = issuer.issue(user.getId(), device());

        sessionManagement.revokeSession(user.getId(), another.sessionId(), AuthSessionRevocationReason.USER_REVOKED);
        sessionManagement.revokeOthers(user.getId(), current.sessionId());

        assertThat(sessions.findById(current.sessionId()).orElseThrow().getRevokedAt()).isNull();
        assertThat(sessions.findById(another.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessions.findById(third.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void concurrentOnboardingTicketsForOneGoogleIdentityCommitExactlyOneCompleteUser() throws Exception {
        String subject = "google-race-" + UUID.randomUUID();
        String email = "race-" + UUID.randomUUID() + "@example.com";
        String firstTicket = ((GoogleAuthResult.Onboarding) google.handleLogin(claims(subject, email))).ticket().rawToken();
        String secondTicket = ((GoogleAuthResult.Onboarding) google.handleLogin(claims(subject, email))).ticket().rawToken();
        CyclicBarrier ready = new CyclicBarrier(2);
        Callable<ErrorCode> completeFirst = () -> {
            ready.await();
            try { onboarding.completeOnboarding(firstTicket, "Race-Password-42", "onboarding-race"); return null; }
            catch (ApiException exception) { return exception.code(); }
        };
        Callable<ErrorCode> completeSecond = () -> {
            ready.await();
            try { onboarding.completeOnboarding(secondTicket, "Race-Password-42", "onboarding-race"); return null; }
            catch (ApiException exception) { return exception.code(); }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, UUID.randomUUID().toString()));
        try {
            Future<ErrorCode> left = pool.submit(completeFirst);
            Future<ErrorCode> right = pool.submit(completeSecond);
            assertThat(java.util.Arrays.asList(left.get(10, java.util.concurrent.TimeUnit.SECONDS), right.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(null, ErrorCode.IDENTITY_LINK_CONFLICT);
        } finally { pool.shutdownNow(); }
        UUID userId = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).orElseThrow().getUserId();
        assertThat(users.count()).isEqualTo(1);
        assertThat(roles.findAllByUserId(userId)).hasSize(1);
        assertThat(sessions.findAllByUserIdOrderByCreatedAtDesc(userId)).hasSize(1);
    }

    private ErrorCode refreshOutcome(String rawRefreshToken, String requestId) {
        try { refresh.refresh(rawRefreshToken, requestId); return null; }
        catch (ApiException exception) { return exception.code(); }
    }

    private java.util.List<ErrorCode> race(Callable<ErrorCode> leftCall, Callable<ErrorCode> rightCall) throws Exception {
        CyclicBarrier ready = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> left = pool.submit(() -> { ready.await(); return leftCall.call(); });
            Future<ErrorCode> right = pool.submit(() -> { ready.await(); return rightCall.call(); });
            return java.util.Arrays.asList(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    private void assertRevokedFamily(UUID sessionId) {
        assertThat(sessions.findById(sessionId).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokens.findAllBySessionId(sessionId)).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }
}

abstract class AuthAcceptanceSupport extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GoogleAuthService google;
    @Autowired AuthApplicationService onboarding;
    @Autowired EmailPasswordLoginService emailPasswords;
    @Autowired PasswordRecoveryService recovery;
    @Autowired RefreshTokenService refresh;
    @Autowired SessionManagementService sessionManagement;
    @Autowired vn.covie.auth.application.InitialSessionIssuer issuer;
    @Autowired vn.covie.auth.security.JwtTokenService jwt;
    @Autowired TokenFingerprint fingerprints;
    @Autowired PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired SystemRoleAssignmentRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthRefreshTokenRepository refreshTokens;
    @Autowired AuthActionTicketRepository tickets;
    @Autowired AuditLogRepository audit;
    @Autowired vn.covie.users.persistence.UserAddressRepository addresses;
    @Autowired vn.covie.users.persistence.UserContactMethodRepository contacts;

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("truncate table audit_logs, auth_refresh_tokens, auth_action_tickets, auth_sessions, system_role_assignments, user_contact_methods, user_addresses, user_identities, users cascade");
    }

    UserEntity activeUser(String name) {
        Instant now = Instant.now();
        UserEntity user = users.save(UserEntity.active(UUID.randomUUID(), name, now));
        roles.save(SystemRoleAssignmentEntity.assign(UUID.randomUUID(), user.getId(), SystemRole.USER, now));
        return user;
    }

    GoogleClaims claims(String subject, String email) { return new GoogleClaims(subject, email, true, "Acceptance User"); }
    vn.covie.auth.model.DeviceMetadata device() { return new vn.covie.auth.model.DeviceMetadata("Acceptance browser", "acceptance-agent"); }
}
