package vn.covie.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.covie.audit.AuditLogRepository;
import vn.covie.auth.model.AuthActionPurpose;
import vn.covie.auth.model.AuthActionTicketEntity;
import vn.covie.auth.persistence.AuthActionTicketRepository;
import vn.covie.auth.persistence.AuthRefreshTokenRepository;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.token.TokenFingerprint;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;

class AuthOnboardingIntegrationTest extends PostgresIntegrationTest {
    @Autowired AuthApplicationService service;
    @Autowired AuthActionTicketRepository tickets;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthRefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired SystemRoleAssignmentRepository roles;
    @Autowired AuditLogRepository audit;
    @Autowired TokenFingerprint fingerprints;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void consumesTicketAndAtomicallyCreatesUserIdentitiesRoleSessionAndAudit() {
        Instant now = Instant.now();
        long usersBefore = users.count();
        long auditsBefore = audit.count();
        String rawTicket = "onboarding-ticket-" + UUID.randomUUID();
        String email = "onboarding-" + UUID.randomUUID() + "@example.com";
        AuthActionTicketEntity ticket = tickets.save(AuthActionTicketEntity.create(UUID.randomUUID(), AuthActionPurpose.ONBOARDING,
                fingerprints.sha256Hex(rawTicket), null, "google-onboard-" + UUID.randomUUID(), email, true,
                "Minh", now, now.plusSeconds(3600)));

        IssuedSession issued = service.completeOnboarding(rawTicket, "correct horse battery staple", "request-onboarding");

        assertThat(users.count()).isEqualTo(usersBefore + 1);
        var google = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, ticket.getGoogleSubject());
        assertThat(google).isPresent();
        assertThat(identities.findLocalEmail(email)).get().satisfies(identity -> assertThat(passwordEncoder.matches("correct horse battery staple", identity.getPasswordHash())).isTrue());
        assertThat(roles.findAllByUserId(google.get().getUserId())).extracting(assignment -> assignment.getRole()).containsExactly(SystemRole.USER);
        assertThat(sessions.findById(issued.sessionId())).isPresent();
        assertThat(refreshTokens.findAllBySessionId(issued.sessionId())).hasSize(1);
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(audit.count()).isEqualTo(auditsBefore + 1);
    }

    @Test
    void rejectsASecondSubmissionOfTheSameOnboardingTicket() {
        Instant now = Instant.now();
        String rawTicket = "onboarding-ticket-" + UUID.randomUUID();
        tickets.save(AuthActionTicketEntity.create(UUID.randomUUID(), AuthActionPurpose.ONBOARDING, fingerprints.sha256Hex(rawTicket), null,
                "google-onboard-" + UUID.randomUUID(), "repeat-" + UUID.randomUUID() + "@example.com", true, "Minh", now, now.plusSeconds(3600)));
        service.completeOnboarding(rawTicket, "correct horse battery staple", "request-onboarding");

        assertThatThrownBy(() -> service.completeOnboarding(rawTicket, "correct horse battery staple", "request-onboarding"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.AUTH_ACTION_ALREADY_CONSUMED);
    }
}
