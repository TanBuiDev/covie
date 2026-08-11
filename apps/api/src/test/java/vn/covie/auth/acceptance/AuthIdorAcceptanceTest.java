package vn.covie.auth.acceptance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import vn.covie.auth.application.IssuedSession;
import vn.covie.users.model.AccountStatus;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.model.UserContactMethodEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserAddressRepository;
import vn.covie.users.persistence.UserContactMethodRepository;

/** Ensures every private-resource lookup scopes the identifier to the authenticated user. */
@AutoConfigureMockMvc
class AuthIdorAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired UserAddressRepository addresses;
    @Autowired UserContactMethodRepository contacts;

    @Test
    void userCannotReadOrMutateAnotherUsersAddressContactOrSession() throws Exception {
        UserEntity attacker = activeUser("Attacker");
        UserEntity victim = activeUser("Victim");
        IssuedSession attackerSession = issuer.issue(attacker.getId(), device());
        IssuedSession victimSession = issuer.issue(victim.getId(), device());
        assertThat(sessionManagement.requireActiveSession(attackerSession.sessionId()).getUserId()).isEqualTo(attacker.getId());
        UserAddressEntity address = addresses.save(UserAddressEntity.create(UUID.randomUUID(), victim.getId(), "Victim home", "Victim", "0123456789", "2 Private Road", 10, 106, PinSource.GPS, null, true, Instant.now()));
        UserContactMethodEntity contact = contacts.save(UserContactMethodEntity.create(UUID.randomUUID(), victim.getId(), ContactType.PHONE, "0123456789", true, Instant.now()));
        String bearer = "Bearer " + attackerSession.accessToken().value();
        String update = "{\"label\":\"Changed\",\"addressText\":\"Nope\",\"latitude\":10.0,\"longitude\":106.0,\"pinSource\":\"GPS\",\"defaultAddress\":false}";

        mvc.perform(delete("/api/v1/me/security/sessions/{id}", victimSession.sessionId()).header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/addresses/{id}", address.getId()).header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/me/addresses/{id}", address.getId()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/contact-methods/{id}", contact.getId()).header("Authorization", bearer)).andExpect(status().isNotFound());
    }

    @Test
    void validJwtAndSessionAreRejectedAfterEachNonActiveAccountStatus() throws Exception {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.BANNED, AccountStatus.DELETED}) {
            clearDatabase();
            UserEntity user = activeUser("Status " + status);
            IssuedSession session = issuer.issue(user.getId(), device());
            user.setAccountStatus(status, Instant.now());
            users.saveAndFlush(user);
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + session.accessToken().value())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void logoutAndGoogleRecoveryRejectThePreviouslyUsableAccessJwt() throws Exception {
        UserEntity logoutUser = activeUser("Logout JWT user");
        IssuedSession logoutSession = issuer.issue(logoutUser.getId(), device());
        String logoutBearer = "Bearer " + logoutSession.accessToken().value();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", logoutBearer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", logoutBearer)).andExpect(status().isUnauthorized());

        UserEntity recoveryUser = activeUser("Recovery JWT user");
        String subject = "jwt-recovery-" + UUID.randomUUID();
        String email = "jwt-recovery-" + UUID.randomUUID() + "@example.com";
        identities.save(vn.covie.users.model.UserIdentityEntity.google(UUID.randomUUID(), recoveryUser.getId(), subject, email, Instant.now()));
        identities.save(vn.covie.users.model.UserIdentityEntity.email(UUID.randomUUID(), recoveryUser.getId(), email, passwords.encode("Recovery-Jwt-Password-42"), Instant.now()));
        IssuedSession old = issuer.issue(recoveryUser.getId(), device());
        String ticket = ((vn.covie.auth.application.GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket().rawToken();
        recovery.resetWithGoogle(ticket, "Recovery-Jwt-New-Password-42", "recovery-old-jwt");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + old.accessToken().value())).andExpect(status().isUnauthorized());
    }
}
