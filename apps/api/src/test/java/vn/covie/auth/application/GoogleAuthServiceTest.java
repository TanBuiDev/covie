package vn.covie.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.security.AuthProperties;
import vn.covie.auth.token.OpaqueTokenGenerator;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.security.EmailNormalizer;

class GoogleAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void createsOnboardingTicketForNewVerifiedGoogleSubject() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(identities.findLocalEmail("minh@example.com")).thenReturn(Optional.empty());

        GoogleAuthService service = service(identities, mock(UserRepository.class), mock(AuthActionTicketRepository.class));

        GoogleAuthResult result = service.handleLogin(new GoogleClaims("google-new", "minh@example.com", true, "Minh"));

        assertThat(result).isInstanceOf(GoogleAuthResult.Onboarding.class);
        GoogleAuthResult.Onboarding onboarding = (GoogleAuthResult.Onboarding) result;
        assertThat(onboarding.ticket().purpose()).isEqualTo(AuthActionPurpose.ONBOARDING);
        assertThat(onboarding.ticket().userId()).isNull();
        assertThat(onboarding.ticket().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void rejectsNewGoogleSubjectWhenLocalEmailAlreadyBelongsToAnotherAccount() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(identities.findLocalEmail("minh@example.com")).thenReturn(Optional.of(UserIdentityEntity.email(UUID.randomUUID(), UUID.randomUUID(), "minh@example.com", "hash", NOW)));

        GoogleAuthService service = service(identities, mock(UserRepository.class), mock(AuthActionTicketRepository.class));

        assertThatThrownBy(() -> service.handleLogin(new GoogleClaims("google-new", "minh@example.com", true, "Minh")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.IDENTITY_LINK_CONFLICT);
    }

    @Test
    void rejectsUnverifiedGoogleEmailForNewOnboarding() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        GoogleAuthService service = service(identities, mock(UserRepository.class), mock(AuthActionTicketRepository.class));

        assertThatThrownBy(() -> service.handleLogin(new GoogleClaims("google-new", "minh@example.com", false, "Minh")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
    }

    @Test
    void rejectsBlankGoogleSubject() {
        GoogleAuthService service = service(mock(UserIdentityRepository.class), mock(UserRepository.class), mock(AuthActionTicketRepository.class));

        assertThatThrownBy(() -> service.handleLogin(new GoogleClaims(" ", "minh@example.com", true, "Minh")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.GOOGLE_IDENTITY_INVALID);
    }

    @Test
    void issuesSessionForExistingGoogleSubjectWithoutLookingUpEmailIdentity() {
        UUID userId = UUID.randomUUID();
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.of(UserIdentityEntity.google(UUID.randomUUID(), userId, "google-existing", "old@example.com", NOW)));
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(UserEntity.active(userId, "Minh", NOW)));
        InitialSessionIssuer issuer = mock(InitialSessionIssuer.class);
        when(issuer.issue(any(), any())).thenReturn(new IssuedSession(UUID.randomUUID(), "refresh", NOW.plus(Duration.ofDays(30)), null));
        GoogleAuthService service = service(identities, users, mock(AuthActionTicketRepository.class), issuer);

        GoogleAuthResult result = service.handleLogin(new GoogleClaims("google-existing", "new@example.com", true, "Minh"), new DeviceMetadata("Linux", "UA"));

        assertThat(result).isInstanceOf(GoogleAuthResult.ExistingUser.class);
        assertThat(((GoogleAuthResult.ExistingUser) result).userId()).isEqualTo(userId);
    }

    @Test
    void createsPasswordResetTicketOnlyForKnownGoogleSubject() {
        UUID userId = UUID.randomUUID();
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.of(UserIdentityEntity.google(UUID.randomUUID(), userId, "google-existing", "minh@example.com", NOW)));
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(UserEntity.active(userId, "Minh", NOW)));
        GoogleAuthService service = service(identities, users, mock(AuthActionTicketRepository.class));

        GoogleAuthResult.PasswordReset result = service.handlePasswordReset(new GoogleClaims("google-existing", "minh@example.com", true, "Minh"));

        assertThat(result.ticket().purpose()).isEqualTo(AuthActionPurpose.PASSWORD_RESET);
        assertThat(result.ticket().userId()).isEqualTo(userId);
    }

    @Test
    void rejectsPasswordResetForUnknownGoogleSubject() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        GoogleAuthService service = service(identities, mock(UserRepository.class), mock(AuthActionTicketRepository.class));

        assertThatThrownBy(() -> service.handlePasswordReset(new GoogleClaims("unknown", "minh@example.com", true, "Minh")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.PASSWORD_RECOVERY_NOT_AVAILABLE);
    }

    private GoogleAuthService service(UserIdentityRepository identities, UserRepository users, AuthActionTicketRepository tickets) {
        return service(identities, users, tickets, mock(InitialSessionIssuer.class));
    }
    private GoogleAuthService service(UserIdentityRepository identities, UserRepository users, AuthActionTicketRepository tickets, InitialSessionIssuer issuer) {
        AppClock clock = () -> NOW;
        return new GoogleAuthService(identities, users, tickets, new OpaqueTokenGenerator(), new TokenFingerprint(),
                new AuthProperties("https://covie.test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10),
                        new AuthProperties.Jwt("test", null, null), new AuthProperties.Cookies(false, "Lax", "at", "rt", "action")),
                new UuidV7Generator(), clock, issuer, new EmailNormalizer(), mock(AuditLogWriter.class), mock(RequestIdAccessor.class));
    }
}
