package vn.covie.auth.application;

import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthActionTicketEntity;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.security.AuthProperties;
import vn.covie.auth.token.OpaqueTokenGenerator;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.security.EmailNormalizer;

@Service
public class GoogleAuthService {
    private final UserIdentityRepository identities;
    private final UserRepository users;
    private final AuthActionTicketRepository tickets;
    private final OpaqueTokenGenerator tokens;
    private final TokenFingerprint fingerprints;
    private final AuthProperties properties;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final InitialSessionIssuer sessionIssuer;
    private final EmailNormalizer emailNormalizer;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;

    public GoogleAuthService(UserIdentityRepository identities, UserRepository users, AuthActionTicketRepository tickets,
            OpaqueTokenGenerator tokens, TokenFingerprint fingerprints, AuthProperties properties, UuidV7Generator ids,
            AppClock clock, InitialSessionIssuer sessionIssuer, EmailNormalizer emailNormalizer, AuditLogWriter audit, RequestIdAccessor requestIds) {
        this.identities = identities; this.users = users; this.tickets = tickets; this.tokens = tokens;
        this.fingerprints = fingerprints; this.properties = properties; this.ids = ids; this.clock = clock;
        this.sessionIssuer = sessionIssuer;
        this.emailNormalizer = emailNormalizer;
        this.audit = audit; this.requestIds = requestIds;
    }

    @Transactional
    public GoogleAuthResult handleLogin(GoogleClaims claims) { return handleLogin(claims, new DeviceMetadata("Browser", "Unknown")); }

    @Transactional
    public GoogleAuthResult handleLogin(GoogleClaims claims, DeviceMetadata device) {
        ValidatedClaims validated = validate(claims);
        Optional<UserIdentityEntity> googleIdentity = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, validated.subject());
        if (googleIdentity.isPresent()) {
            UserEntity user = users.findById(googleIdentity.get().getUserId()).orElseThrow(() -> invalidIdentity());
            requireActive(user);
            googleIdentity.get().markUsed(clock.now());
            GoogleAuthResult.ExistingUser result = new GoogleAuthResult.ExistingUser(user.getId(), sessionIssuer.issue(user.getId(), device));
            audit.securityEvent(user.getId(), "GOOGLE_LOGIN", "USER", user.getId(), requestIds.current(), java.util.Map.of());
            return result;
        }
        if (identities.findLocalEmail(validated.email()).isPresent()) throw conflict();
        return new GoogleAuthResult.Onboarding(issueTicket(AuthActionPurpose.ONBOARDING, null, validated));
    }

    @Transactional
    public GoogleAuthResult.PasswordReset handlePasswordReset(GoogleClaims claims) {
        ValidatedClaims validated = validate(claims);
        UserIdentityEntity identity = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, validated.subject())
                .orElseThrow(() -> new ApiException(ErrorCode.PASSWORD_RECOVERY_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Google password recovery is unavailable"));
        UserEntity user = users.findById(identity.getUserId()).orElseThrow(this::invalidIdentity);
        requireActive(user);
        identity.markUsed(clock.now());
        return new GoogleAuthResult.PasswordReset(issueTicket(AuthActionPurpose.PASSWORD_RESET, user.getId(), validated));
    }

    private ValidatedClaims validate(GoogleClaims claims) {
        if (claims == null || claims.subject() == null || claims.subject().isBlank()) throw invalidIdentity();
        if (claims.email() == null || claims.email().isBlank()) throw invalidIdentity();
        if (!Boolean.TRUE.equals(claims.emailVerified())) throw new ApiException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED, HttpStatus.UNPROCESSABLE_ENTITY, "Google email must be verified");
        String email = emailNormalizer.normalize(claims.email());
        String displayName = claims.displayName() == null || claims.displayName().isBlank() ? email.substring(0, email.indexOf('@')) : claims.displayName().trim();
        return new ValidatedClaims(claims.subject().trim(), email, displayName);
    }

    private IssuedAuthActionTicket issueTicket(AuthActionPurpose purpose, java.util.UUID userId, ValidatedClaims claims) {
        Instant now = clock.now(); Instant expiresAt = now.plus(properties.actionTicketTtl()); String rawToken = tokens.generate256BitToken();
        tickets.save(AuthActionTicketEntity.create(ids.generate(), purpose, fingerprints.sha256Hex(rawToken), userId, claims.subject(), claims.email(), true, claims.displayName(), now, expiresAt));
        return new IssuedAuthActionTicket(rawToken, purpose, expiresAt, userId);
    }
    private ApiException invalidIdentity() { return new ApiException(ErrorCode.GOOGLE_IDENTITY_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Google identity is invalid"); }
    private ApiException conflict() { return new ApiException(ErrorCode.IDENTITY_LINK_CONFLICT, HttpStatus.CONFLICT, "Google identity cannot be linked automatically"); }
    private void requireActive(UserEntity user) { if (!user.isActive()) throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN, "Account is not active"); }
    private record ValidatedClaims(String subject, String email, String displayName) {}
}
