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
import vn.covie.auth.application.IssuedSession;
import vn.covie.users.model.UserEntity;

/** Browser cookie and CSRF behavior against the installed security filter chain. */
@AutoConfigureMockMvc
class AuthCookieCsrfAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;

    @Test
    void browserCookieMutationRequiresValidCsrfButBearerAndGetsDoNot() throws Exception {
        UserEntity user = activeUser("CSRF user");
        IssuedSession session = issuer.issue(user.getId(), device());
        assertThat(sessionManagement.requireActiveSession(session.sessionId()).getUserId()).isEqualTo(user.getId());
        Cookie access = new Cookie("covie_at", session.accessToken().value());
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
    }
}
