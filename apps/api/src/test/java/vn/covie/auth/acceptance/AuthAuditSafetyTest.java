package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.slf4j.LoggerFactory;
import vn.covie.auth.application.GoogleAuthResult;
import vn.covie.auth.application.IssuedAuthActionTicket;
import vn.covie.auth.oauth.GoogleOAuthFailureHandler;
import vn.covie.auth.security.AuthProperties;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.model.UserIdentityEntity;

/** Fixture values are deliberately improbable so this catches accidental credential serialization. */
@AutoConfigureMockMvc
class AuthAuditSafetyTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired AuthProperties authProperties;
    @Autowired GoogleOAuthFailureHandler oauthFailure;
    @Test
    void loginRefreshReuseGoogleResetAndOauthFailureNeverLeakDistinctiveSubmittedSecrets() throws Exception {
        String oldPassword = "AUDIT-OLD-PASSWORD-89dfc7";
        String newPassword = "AUDIT-NEW-PASSWORD-6ab432";
        String csrf = "AUDIT-CSRF-TOKEN-c91e38";
        String oauthCode = "AUDIT-OAUTH-AUTHORIZATION-CODE-6c7bf2";
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        String subject = "audit-google-" + UUID.randomUUID();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); root.addAppender(logs); logs.start();
        String loginAccess = null; String loginRefresh = null; String rotatedAccess = null; String rotatedRefresh = null; IssuedAuthActionTicket reset = null; String resetAccess = null; String resetRefresh = null;
        try {
            UserEntity user = activeUser("Audit user");
            identities.save(UserIdentityEntity.google(UUID.randomUUID(), user.getId(), subject, email, Instant.now()));
            identities.save(UserIdentityEntity.email(UUID.randomUUID(), user.getId(), email, passwords.encode(oldPassword), Instant.now()));
            Cookie loginCsrf = csrfCookie();
            MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(loginCsrf).header("X-CSRF-Token", loginCsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"" + oldPassword + "\"}"))
                    .andExpect(status().isOk()).andReturn();
            loginAccess = responseCookieValue(login, authProperties.cookies().accessName()); loginRefresh = responseCookieValue(login, authProperties.cookies().refreshName());
            Cookie refreshCsrf = new Cookie("COVIE-XSRF-TOKEN", csrf);
            MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(authProperties.cookies().refreshName(), loginRefresh), refreshCsrf).header("X-CSRF-Token", refreshCsrf.getValue()))
                    .andExpect(status().isOk()).andReturn();
            rotatedAccess = responseCookieValue(rotated, authProperties.cookies().accessName()); rotatedRefresh = responseCookieValue(rotated, authProperties.cookies().refreshName());
            Cookie reuseCsrf = csrfCookie();
            mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(authProperties.cookies().refreshName(), loginRefresh), reuseCsrf).header("X-CSRF-Token", reuseCsrf.getValue()))
                    .andExpect(status().isUnauthorized());
            reset = ((GoogleAuthResult.PasswordReset) google.handlePasswordReset(claims(subject, email))).ticket();
            Cookie resetCsrf = csrfCookie();
            MvcResult resetResult = mvc.perform(post("/api/v1/auth/password/reset-with-google")
                            .cookie(new Cookie(authProperties.cookies().actionName(), reset.rawToken()), resetCsrf).header("X-CSRF-Token", resetCsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + newPassword + "\"}"))
                    .andExpect(status().isOk()).andReturn();
            resetAccess = responseCookieValue(resetResult, authProperties.cookies().accessName()); resetRefresh = responseCookieValue(resetResult, authProperties.cookies().refreshName());
            MockHttpServletRequest oauthCallback = new MockHttpServletRequest("GET", "/api/v1/auth/google/callback/google");
            oauthCallback.setParameter("code", oauthCode);
            oauthFailure.onAuthenticationFailure(oauthCallback, new MockHttpServletResponse(), new AuthenticationException("OAuth callback failed") {});
        } finally { logs.stop(); root.detachAppender(logs); }

        String auditJson = jdbc.queryForObject("select coalesce(string_agg(coalesce(after_snapshot::text, ''), ' '), '') from audit_logs", String.class);
        String capturedLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action in ('LOGIN_SUCCESS','REFRESH_TOKEN_REUSE_DETECTED','PASSWORD_RESET','GOOGLE_LOGIN_FAILED')", Integer.class)).isEqualTo(4);
        assertThat(auditJson + "\n" + capturedLogs).doesNotContain(oldPassword, newPassword, loginAccess, loginRefresh, rotatedAccess, rotatedRefresh, reset.rawToken(), resetAccess, resetRefresh, csrf, oauthCode);
    }

    private Cookie csrfCookie() throws Exception { return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("COVIE-XSRF-TOKEN"); }
    private String responseCookieValue(MvcResult result, String name) { return result.getResponse().getCookie(name).getValue(); }
}
