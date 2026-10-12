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
    @Autowired vn.covie.realtime.SseConnectionRegistry streams;
    @Autowired vn.covie.auth.security.AuthProperties authProperties;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void signedJwtStreamsIsolateRecipientsAcrossTabsAndReconnect(boolean browserCookie) throws Exception {
        var first = activeUser("SSE first user");
        var second = activeUser("SSE second user");
        String firstBearer = "Bearer " + issuer.issue(first.getId(), device()).accessToken().value();
        String secondBearer = "Bearer " + issuer.issue(second.getId(), device()).accessToken().value();
        var firstTab = mvc.perform(streamRequest(firstBearer, browserCookie))
                .andExpect(status().isOk()).andReturn();
        var otherTab = mvc.perform(streamRequest(firstBearer, browserCookie))
                .andExpect(status().isOk()).andReturn();
        var secondTab = mvc.perform(streamRequest(secondBearer, browserCookie))
                .andExpect(status().isOk()).andReturn();
        org.springframework.test.web.servlet.MvcResult reconnected = null;
        try {
            streams.publish(new vn.covie.realtime.SseSignal(first.getId(), "order.updated",
                    java.util.Map.of("orderId", "first-private-order")));
            assertThat(firstTab.getResponse().getContentAsString()).contains("first-private-order");
            assertThat(otherTab.getResponse().getContentAsString()).contains("first-private-order");
            assertThat(secondTab.getResponse().getContentAsString()).doesNotContain("first-private-order");
            streams.publish(new vn.covie.realtime.SseSignal(second.getId(), "notification.created",
                    java.util.Map.of("notificationId", "second-private-notification")));
            assertThat(secondTab.getResponse().getContentAsString()).contains("second-private-notification");
            assertThat(firstTab.getResponse().getContentAsString()).doesNotContain("second-private-notification");
            assertThat(otherTab.getResponse().getContentAsString()).doesNotContain("second-private-notification");
            firstTab.getRequest().getAsyncContext().complete();
            reconnected = mvc.perform(streamRequest(firstBearer, browserCookie))
                    .andExpect(status().isOk()).andReturn();
            assertThat(reconnected.getResponse().getContentAsString()).contains("sync-required")
                    .doesNotContain("first-private-order");
            streams.publish(new vn.covie.realtime.SseSignal(first.getId(), "order.updated",
                    java.util.Map.of("orderId", "after-reconnect")));
            assertThat(reconnected.getResponse().getContentAsString()).contains("after-reconnect");
            assertThat(otherTab.getResponse().getContentAsString()).contains("after-reconnect");
            assertThat(secondTab.getResponse().getContentAsString()).doesNotContain("after-reconnect");
        } finally {
            if (reconnected != null) reconnected.getRequest().getAsyncContext().complete();
            otherTab.getRequest().getAsyncContext().complete();
            secondTab.getRequest().getAsyncContext().complete();
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder streamRequest(
            String bearer, boolean browserCookie) {
        var request = get("/api/v1/me/events");
        return browserCookie
                ? request.cookie(new jakarta.servlet.http.Cookie(authProperties.cookies().accessName(), bearer.substring(7)))
                : request.header("Authorization", bearer);
    }

    @Test
    void eventStreamRequiresAnActiveSessionAndReconnectAfterLogoutIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/events")).andExpect(status().isUnauthorized());
        UserEntity user = activeUser("SSE session");
        IssuedSession session = issuer.issue(user.getId(), device());
        String bearer = "Bearer " + session.accessToken().value();
        var stream = mvc.perform(get("/api/v1/me/events").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        assertThat(stream.getResponse().getContentAsString()).contains("sync-required");
        stream.getRequest().getAsyncContext().complete();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/events").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatedCredentialsCannotRestoreAccessForANonActiveAccount() throws Exception {
        for (AccountStatus accountStatus : new AccountStatus[]{AccountStatus.SUSPENDED, AccountStatus.BANNED, AccountStatus.DELETED}) {
            UserEntity user = activeUser("Non-active refresh " + accountStatus);
            IssuedSession original = issuer.issue(user.getId(), device());
            user.setAccountStatus(accountStatus, Instant.now());
            users.saveAndFlush(user);
            IssuedSession rotated = refresh.refresh(original.rawRefreshToken(), "nonactive-refresh-" + accountStatus);
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + rotated.accessToken().value()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/me/security/sessions").header("Authorization", "Bearer " + rotated.accessToken().value()))
                    .andExpect(status().isUnauthorized());
        }
    }

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
