package vn.covie.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class AuthOpenApiContractTest extends PostgresIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void documentsPlanTwoAuthAndPersonalEndpointsWithoutRefreshTokenBodies() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json)
                .contains("/api/v1/auth/csrf")
                .contains("/api/v1/auth/login")
                .contains("/api/v1/auth/refresh")
                .contains("/api/v1/auth/logout")
                .contains("/api/v1/auth/onboarding/set-password")
                .contains("/api/v1/auth/password/reset-with-google")
                .contains("/api/v1/me")
                .contains("/api/v1/me/security/sessions")
                .contains("/api/v1/me/addresses")
                .contains("/api/v1/me/contact-methods")
                .contains("\"securitySchemes\":{\"bearerAuth\"")
                .contains("Bearer JWT")
                .contains("X-CSRF-Token")
                .contains("HttpOnly")
                .contains("rotates refresh cookie")
                .doesNotContain("refreshToken");
    }
}
