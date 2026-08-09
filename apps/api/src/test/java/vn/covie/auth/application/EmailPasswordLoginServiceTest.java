package vn.covie.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.security.EmailNormalizer;

class EmailPasswordLoginServiceTest {
    @Test
    void hidesWhetherEmailExistsWhenCredentialsAreInvalid() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode(any())).thenReturn("dummy-hash");
        EmailPasswordLoginService service = new EmailPasswordLoginService(identities, mock(UserRepository.class), passwords,
                new EmailNormalizer(), mock(InitialSessionIssuer.class), mock(AuditLogWriter.class));

        when(identities.findLocalEmail("unknown@example.com")).thenReturn(Optional.empty());
        ApiException unknown = failure(() -> service.login("unknown@example.com", "wrong-password", new DeviceMetadata("Browser", "UA"), "req-1"));
        when(identities.findLocalEmail("known@example.com")).thenReturn(Optional.of(UserIdentityEntity.email(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "known@example.com", "stored-hash", java.time.Instant.now())));
        when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);
        ApiException wrongPassword = failure(() -> service.login("known@example.com", "wrong-password", new DeviceMetadata("Browser", "UA"), "req-2"));

        assertThat(unknown.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknown.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getMessage()).isEqualTo("Invalid email or password.");
        assertThat(wrongPassword.code()).isEqualTo(unknown.code());
        assertThat(wrongPassword.status()).isEqualTo(unknown.status());
        assertThat(wrongPassword.getMessage()).isEqualTo(unknown.getMessage());
    }

    @Test
    void createsIndependentSessionForActiveUserWithValidPassword() {
        UUID userId = UUID.randomUUID();
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode(any())).thenReturn("dummy-hash");
        UserIdentityEntity identity = UserIdentityEntity.email(UUID.randomUUID(), userId, "minh@example.com", "stored-hash", java.time.Instant.now());
        when(identities.findLocalEmail("minh@example.com")).thenReturn(Optional.of(identity));
        when(passwords.matches("correct horse battery staple", "stored-hash")).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(UserEntity.active(userId, "Minh", java.time.Instant.now())));
        InitialSessionIssuer issuer = mock(InitialSessionIssuer.class);
        IssuedSession issued = new IssuedSession(UUID.randomUUID(), "refresh", java.time.Instant.now().plusSeconds(3600), null);
        when(issuer.issue(eq(userId), any())).thenReturn(issued);
        EmailPasswordLoginService service = new EmailPasswordLoginService(identities, users, passwords, new EmailNormalizer(), issuer, mock(AuditLogWriter.class));

        IssuedSession result = service.login("minh@example.com", "correct horse battery staple", new DeviceMetadata("Browser", "UA"), "req-success");

        assertThat(result).isSameAs(issued);
    }

    private ApiException failure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return (ApiException) assertThatThrownBy(call).isInstanceOf(ApiException.class).actual();
    }
}
