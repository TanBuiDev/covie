package vn.covie.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class AuthOpenApiContractTest extends PostgresIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void documentsPlanTwoAuthAndPersonalEndpointsWithoutRefreshTokenBodies() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");

        assertThat(paths.fieldNames()).toIterable().contains(
                "/api/v1/auth/csrf",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/onboarding/set-password",
                "/api/v1/auth/password/reset-with-google",
                "/api/v1/me",
                "/api/v1/me/security/sessions",
                "/api/v1/me/addresses",
                "/api/v1/me/contact-methods");

        JsonNode csrf = paths.path("/api/v1/auth/csrf").path("get");
        assertThat(csrf.path("parameters").findValuesAsText("name")).doesNotContain("token");
        JsonNode csrfBody = csrf.path("responses").path("200").path("content").path("*/*").path("schema");
        assertThat(csrfBody.path("$ref").asText()).isEqualTo("#/components/schemas/CsrfTokenResponse");
        assertThat(document.at("/components/schemas/CsrfTokenResponse/properties/headerName/type").asText()).isEqualTo("string");
        assertThat(document.at("/components/schemas/CsrfTokenResponse/properties/token/type").asText()).isEqualTo("string");

        assertNoContent(paths.path("/api/v1/auth/logout").path("post"));
        assertNoContent(paths.path("/api/v1/me/security/sessions/{sessionId}").path("delete"));
        assertNoContent(paths.path("/api/v1/me/security/sessions/revoke-others").path("post"));

        JsonNode bearer = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(json).contains("X-CSRF-Token", "HttpOnly", "rotates refresh cookie").doesNotContain("refreshToken");
    }

    private void assertNoContent(JsonNode operation) {
        assertThat(operation.path("responses").has("204")).isTrue();
        assertThat(operation.path("responses").has("200")).isFalse();
    }
}
