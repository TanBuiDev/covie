package vn.covie.auth.application;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthActionTicketEntity;
import vn.covie.auth.model.AuthSessionRevocationReason;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;

@Service
public class PasswordRecoveryService {
    private final AuthActionTicketRepository tickets; private final UserIdentityRepository identities;
    private final AuthSessionRepository sessions; private final AuthRefreshTokenRepository refreshTokens;
    private final TokenFingerprint fingerprints; private final PasswordEncoder passwords; private final InitialSessionIssuer issuer;
    private final AppClock clock; private final AuditLogWriter audit;
    public PasswordRecoveryService(AuthActionTicketRepository tickets, UserIdentityRepository identities, AuthSessionRepository sessions,
            AuthRefreshTokenRepository refreshTokens, TokenFingerprint fingerprints, PasswordEncoder passwords, InitialSessionIssuer issuer,
            AppClock clock, AuditLogWriter audit) { this.tickets=tickets; this.identities=identities; this.sessions=sessions; this.refreshTokens=refreshTokens; this.fingerprints=fingerprints; this.passwords=passwords; this.issuer=issuer; this.clock=clock; this.audit=audit; }
    @Transactional
    public IssuedSession resetWithGoogle(String rawTicket, String newPassword, String requestId) {
        if (rawTicket == null || rawTicket.isBlank()) throw unavailable();
        Instant now=clock.now();
        AuthActionTicketEntity ticket=tickets.findByTokenHashForUpdate(fingerprints.sha256Hex(rawTicket)).orElseThrow(this::unavailable);
        if (ticket.getPurpose()!=AuthActionPurpose.PASSWORD_RESET) throw new ApiException(ErrorCode.AUTH_ACTION_PURPOSE_MISMATCH,HttpStatus.CONFLICT,"Authentication action purpose does not match");
        if (ticket.getConsumedAt()!=null) throw new ApiException(ErrorCode.AUTH_ACTION_ALREADY_CONSUMED,HttpStatus.CONFLICT,"Authentication action was already consumed");
        if (!ticket.getExpiresAt().isAfter(now)) throw new ApiException(ErrorCode.AUTH_ACTION_EXPIRED,HttpStatus.UNAUTHORIZED,"Authentication action has expired");
        if (ticket.getUserId()==null) throw unavailable();
        UserIdentityEntity google=identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE,ticket.getGoogleSubject()).orElseThrow(this::unavailable);
        if (!google.getUserId().equals(ticket.getUserId())) throw unavailable();
        UserIdentityEntity email=identities.findLocalEmail(ticket.getEmailNormalized()).filter(value -> value.getUserId().equals(ticket.getUserId())).orElseThrow(this::unavailable);
        sessions.findAllActiveByUserIdForUpdate(ticket.getUserId()).forEach(session -> {
            refreshTokens.findAllBySessionIdForUpdate(session.getId()).forEach(token -> { if (!token.isRevoked()) token.revoke(now); });
            session.revoke(now, AuthSessionRevocationReason.PASSWORD_RESET);
        });
        email.updatePasswordHash(passwords.encode(newPassword)); ticket.consume(now);
        IssuedSession issued=issuer.issue(ticket.getUserId(),new DeviceMetadata("Browser","Unknown"));
        audit.securityEvent(ticket.getUserId(),"PASSWORD_RESET","USER",ticket.getUserId(),requestId,Map.of());
        return issued;
    }
    private ApiException unavailable() { return new ApiException(ErrorCode.PASSWORD_RECOVERY_NOT_AVAILABLE,HttpStatus.CONFLICT,"Password recovery is unavailable"); }
}
