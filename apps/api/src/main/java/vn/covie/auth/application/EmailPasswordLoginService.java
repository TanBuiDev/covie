package vn.covie.auth.application;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.ratelimit.RateLimitFingerprintService;
import vn.covie.ratelimit.RateLimitGroup;
import vn.covie.ratelimit.RedisTokenBucketRateLimiter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.security.EmailNormalizer;

@Service
public class EmailPasswordLoginService {
    private final UserIdentityRepository identities;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final EmailNormalizer emailNormalizer;
    private final InitialSessionIssuer sessions;
    private final AuditLogWriter audit;
    private final String dummyHash;
    private RedisTokenBucketRateLimiter rateLimiter;
    private RateLimitFingerprintService rateLimitFingerprints;

    public EmailPasswordLoginService(UserIdentityRepository identities, UserRepository users, PasswordEncoder passwords,
            EmailNormalizer emailNormalizer, InitialSessionIssuer sessions, AuditLogWriter audit) {
        this.identities = identities; this.users = users; this.passwords = passwords; this.emailNormalizer = emailNormalizer;
        this.sessions = sessions; this.audit = audit; this.dummyHash = passwords.encode("covie-login-dummy-password");
    }

    @Autowired(required = false)
    void configureRateLimiting(RedisTokenBucketRateLimiter limiter, RateLimitFingerprintService fingerprints) {
        this.rateLimiter = limiter;
        this.rateLimitFingerprints = fingerprints;
    }

    @Transactional
    public IssuedSession login(String rawEmail, String rawPassword, DeviceMetadata device, String requestId) {
        String email = emailNormalizer.normalize(rawEmail);
        if (rateLimiter != null && rateLimiter.enabled()) {
            rateLimiter.require(RateLimitGroup.AUTH_LOGIN_EMAIL, rateLimitFingerprints.email(email));
        }
        UserIdentityEntity identity = identities.findLocalEmail(email).orElse(null);
        if (identity == null) {
            passwords.matches(rawPassword, dummyHash);
            throw invalidCredentials(requestId);
        }
        if (!passwords.matches(rawPassword, identity.getPasswordHash())) throw invalidCredentials(requestId);
        UserEntity user = users.findById(identity.getUserId()).orElseThrow(() -> invalidCredentials(requestId));
        if (!user.isActive()) throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN, "Account is not active");
        identity.markUsed(java.time.Instant.now());
        IssuedSession issued = sessions.issue(user.getId(), device);
        audit.securityEvent(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), requestId, Map.of());
        return issued;
    }

    private ApiException invalidCredentials(String requestId) {
        audit.securityFailure("LOGIN_FAILED", requestId, Map.of("reason", "INVALID_CREDENTIALS"));
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
