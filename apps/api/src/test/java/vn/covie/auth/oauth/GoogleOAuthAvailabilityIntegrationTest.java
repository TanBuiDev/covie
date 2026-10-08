package vn.covie.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class GoogleOAuthAvailabilityIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void exposesUnconfiguredStateWithoutAuthenticationOrSession() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/google/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"configured\":false}");
    }

    @Test void rejectsUnconfiguredStartBeforeSessionCreationOrRedirect() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/google/start"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AUTH_PROVIDER_UNAVAILABLE")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }
}
