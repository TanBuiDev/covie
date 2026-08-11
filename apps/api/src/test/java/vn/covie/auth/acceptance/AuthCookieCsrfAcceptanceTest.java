package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.IssuedSession;
import vn.covie.auth.security.AuthProperties;
import vn.covie.users.model.UserEntity;

/** Browser cookie and CSRF behavior against the installed security filter chain. */
@AutoConfigureMockMvc
class AuthCookieCsrfAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired AuthProperties authProperties;

    @Test
    void browserCookieMutationRequiresValidCsrfButBearerAndGetsDoNot() throws Exception {
        UserEntity user = activeUser("CSRF user");
        IssuedSession session = issuer.issue(user.getId(), device());
        assertThat(sessionManagement.requireActiveSession(session.sessionId()).getUserId()).isEqualTo(user.getId());
        Cookie access = new Cookie(authProperties.cookies().accessName(), session.accessToken().value());
        String requestBody = "{\"label\":\"Home\",\"addressText\":\"1 Test Street\",\"latitude\":10.0,\"longitude\":106.0,\"pinSource\":\"GPS\",\"defaultAddress\":true}";

        mvc.perform(post("/api/v1/me/addresses").cookie(access).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/addresses").cookie(access).header("X-CSRF-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("COVIE-XSRF-TOKEN");
        mvc.perform(post("/api/v1/me/addresses").cookie(access, csrfCookie).header("X-CSRF-Token", csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/addresses").header("Authorization", "Bearer " + session.accessToken().value()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
    }

    @Test
    void csrfEndpointProvidesFreshCookieAndLoginInvalidatesIt() throws Exception {
        UserEntity user = activeUser("Login CSRF user");
        String email = "csrf-" + java.util.UUID.randomUUID() + "@example.com";
        identities.save(vn.covie.users.model.UserIdentityEntity.email(java.util.UUID.randomUUID(), user.getId(), email, passwords.encode("Csrf-Password-42"), java.time.Instant.now()));
        MvcResult issued = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = issued.getResponse().getCookie("COVIE-XSRF-TOKEN");

        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-CSRF-Token", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"Csrf-Password-42\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(login.getResponse().getHeaders("Set-Cookie")).anyMatch(value -> value.startsWith("COVIE-XSRF-TOKEN=") && value.contains("Max-Age=0"));
        assertFreshAfterInvalidation(csrfCookie);
    }

    @Test
    void csrfIsInvalidatedThenRegeneratedAfterOnboardingRefreshResetAndLogout() throws Exception {
        String onboardingEmail = "csrf-onboard-" + java.util.UUID.randomUUID() + "@example.com";
        String onboardingTicket = ((GoogleAuthResult.Onboarding) google.handleLogin(claims("csrf-onboard-" + java.util.UUID.randomUUID(), onboardingEmail))).ticket().rawToken();
        Cookie initial = csrfCookie();
        MvcResult onboardingResponse = mvc.perform(post("/api/v1/auth/onboarding/set-password")
                        .cookie(new Cookie(authProperties.cookies().actionName(), onboardingTicket), initial).header("X-CSRF-Token", initial.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Csrf-Onboard-Password-42\"}"))
                .andExpect(status().isOk()).andReturn();
        assertInvalidated(onboardingResponse); Cookie afterOnboarding = assertFreshAfterInvalidation(initial);

        String refreshRaw = cookieValue(onboardingResponse, authProperties.cookies().refreshName());
        MvcResult refreshResponse = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(authProperties.cookies().refreshName(), refreshRaw), afterOnboarding).header("X-CSRF-Token", afterOnboarding.getValue()))
                .andExpect(status().isOk()).andReturn();
        assertInvalidated(refreshResponse); Cookie afterRefresh = assertFreshAfterInvalidation(afterOnboarding);

        UserEntity resetUser = activeUser("CSRF reset user");
        String subject = "csrf-reset-" + java.util.UUID.randomUUID(); String email = "csrf-reset-" + java.util.UUID.randomUUID() + "@example.com";
        identities.save(vn.covie.users.model.UserIdentityEntity.google(java.util.UUID.randomUUID(), resetUser.getId(), subject, email, java.time.Instant.now()));
        identities.save(vn.covie.users.model.UserIdentityEntity.email(java.util.UUID.randomUUID(), resetUser.getId(), email, passwords.encode("Csrf-Reset-Password-42"), java.time.Instant.now()));
        String resetTicket = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket().rawToken();
        MvcResult resetResponse = mvc.perform(post("/api/v1/auth/password/reset-with-google")
                        .cookie(new Cookie(authProperties.cookies().actionName(), resetTicket), afterRefresh).header("X-CSRF-Token", afterRefresh.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Csrf-Reset-New-Password-42\"}"))
                .andExpect(status().isOk()).andReturn();
        assertInvalidated(resetResponse); Cookie afterReset = assertFreshAfterInvalidation(afterRefresh);

        String access = cookieValue(resetResponse, authProperties.cookies().accessName());
        String refresh = cookieValue(resetResponse, authProperties.cookies().refreshName());
        MvcResult logout = mvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(authProperties.cookies().accessName(), access), new Cookie(authProperties.cookies().refreshName(), refresh), afterReset)
                        .header("X-CSRF-Token", afterReset.getValue())).andExpect(status().isNoContent()).andReturn();
        assertInvalidated(logout); assertFreshAfterInvalidation(afterReset);
    }

    @Test
    void staleAccessCookieDoesNotBlockPublicAuthEndpointsOrGoogleRecoveryStart() throws Exception {
        String email = "csrf-stale-" + java.util.UUID.randomUUID() + "@example.com";
        UserEntity user = activeUser("Stale access user");
        identities.save(vn.covie.users.model.UserIdentityEntity.email(java.util.UUID.randomUUID(), user.getId(), email, passwords.encode("Csrf-Stale-Password-42"), java.time.Instant.now()));
        IssuedSession refreshSession = issuer.issue(user.getId(), device());
        Cookie csrf = csrfCookie(); Cookie stale = new Cookie(authProperties.cookies().accessName(), "expired-covie-at-distinctive");
        mvc.perform(post("/api/v1/auth/login").cookie(stale, csrf).header("X-CSRF-Token", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Csrf-Stale-Password-42\"}"))
                .andExpect(status().isOk());
        Cookie refreshCsrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/refresh").cookie(stale, new Cookie(authProperties.cookies().refreshName(), refreshSession.rawRefreshToken()), refreshCsrf).header("X-CSRF-Token", refreshCsrf.getValue()))
                .andExpect(status().isOk());
        String googleSubject = "csrf-stale-google-" + java.util.UUID.randomUUID();
        identities.save(vn.covie.users.model.UserIdentityEntity.google(java.util.UUID.randomUUID(), user.getId(), googleSubject, email, java.time.Instant.now()));
        String resetTicket = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(googleSubject, email))).ticket().rawToken();
        Cookie resetCsrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/password/reset-with-google")
                        .cookie(stale, new Cookie(authProperties.cookies().actionName(), resetTicket), resetCsrf).header("X-CSRF-Token", resetCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Csrf-Stale-Recovery-Password-42\"}"))
                .andExpect(status().isOk());
        Cookie logoutCsrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/logout").cookie(stale, new Cookie(authProperties.cookies().refreshName(), refreshSession.rawRefreshToken()), logoutCsrf).header("X-CSRF-Token", logoutCsrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/google/start").param("intent", "PASSWORD_RESET").cookie(stale)).andExpect(status().is3xxRedirection());
    }

    private Cookie csrfCookie() throws Exception { return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("COVIE-XSRF-TOKEN"); }
    private Cookie assertFreshAfterInvalidation(Cookie previous) throws Exception { Cookie fresh = csrfCookie(); assertThat(fresh.getValue()).isNotEqualTo(previous.getValue()); return fresh; }
    private void assertInvalidated(MvcResult result) { assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(value -> value.startsWith("COVIE-XSRF-TOKEN=") && value.contains("Max-Age=0")); }
    private String cookieValue(MvcResult result, String name) { return result.getResponse().getCookie(name).getValue(); }
}
