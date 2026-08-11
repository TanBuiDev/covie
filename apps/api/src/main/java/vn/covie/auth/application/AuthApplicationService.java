package vn.covie.auth.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthActionTicketEntity;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public class AuthApplicationService {
    private final AuthActionTicketRepository tickets;
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final SystemRoleAssignmentRepository roles;
    private final PasswordEncoder passwords;
    private final InitialSessionIssuer sessions;
    private final TokenFingerprint fingerprints;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;

    public AuthApplicationService(AuthActionTicketRepository tickets, UserRepository users, UserIdentityRepository identities,
            SystemRoleAssignmentRepository roles, PasswordEncoder passwords, InitialSessionIssuer sessions,
            TokenFingerprint fingerprints, UuidV7Generator ids, AppClock clock, AuditLogWriter audit) {
        this.tickets = tickets; this.users = users; this.identities = identities; this.roles = roles; this.passwords = passwords;
        this.sessions = sessions; this.fingerprints = fingerprints; this.ids = ids; this.clock = clock; this.audit = audit;
    }

    @Transactional
    public IssuedSession completeOnboarding(String rawTicket, String rawPassword, String requestId) {
        if (rawTicket == null || rawTicket.isBlank()) throw new ApiException(ErrorCode.AUTH_ACTION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication action is required");
        Instant now = clock.now();
        AuthActionTicketEntity ticket = tickets.findByTokenHashForUpdate(fingerprints.sha256Hex(rawTicket))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_ACTION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication action is required"));
        validateOnboarding(ticket, now);
        if (identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, ticket.getGoogleSubject()).isPresent() || identities.findLocalEmail(ticket.getEmailNormalized()).isPresent()) throw conflict();
        try {
            UUID userId = ids.generate();
            UserEntity user = users.save(UserEntity.active(userId, ticket.getDisplayName(), now));
            identities.saveAndFlush(UserIdentityEntity.google(ids.generate(), userId, ticket.getGoogleSubject(), ticket.getEmailNormalized(), now));
            identities.saveAndFlush(UserIdentityEntity.email(ids.generate(), userId, ticket.getEmailNormalized(), passwords.encode(rawPassword), now));
            roles.save(vn.covie.users.model.SystemRoleAssignmentEntity.assign(ids.generate(), userId, SystemRole.USER, now));
            ticket.consume(now);
            IssuedSession issued = sessions.issue(userId, new DeviceMetadata("Browser", "Unknown"));
            audit.securityEvent(userId, "ONBOARDING_COMPLETED", "USER", userId, requestId, Map.of());
            return issued;
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private void validateOnboarding(AuthActionTicketEntity ticket, Instant now) {
        if (ticket.getPurpose() != AuthActionPurpose.ONBOARDING) throw new ApiException(ErrorCode.AUTH_ACTION_PURPOSE_MISMATCH, HttpStatus.CONFLICT, "Authentication action purpose does not match");
        if (ticket.getConsumedAt() != null) throw new ApiException(ErrorCode.AUTH_ACTION_ALREADY_CONSUMED, HttpStatus.CONFLICT, "Authentication action was already consumed");
        if (!ticket.getExpiresAt().isAfter(now)) throw new ApiException(ErrorCode.AUTH_ACTION_EXPIRED, HttpStatus.UNAUTHORIZED, "Authentication action has expired");
        if (!ticket.isGoogleEmailVerified()) throw new ApiException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED, HttpStatus.UNPROCESSABLE_ENTITY, "Google email must be verified");
    }
    private ApiException conflict() { return new ApiException(ErrorCode.IDENTITY_LINK_CONFLICT, HttpStatus.CONFLICT, "Identity is already linked"); }
}
