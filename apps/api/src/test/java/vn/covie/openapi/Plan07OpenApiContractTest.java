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
class Plan07OpenApiContractTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Test void exposesTrustNotificationAndSseContracts() throws Exception {
        JsonNode document = new ObjectMapper().readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode paths = document.path("paths");
        assertThat(paths.fieldNames()).toIterable().contains("/api/v1/me/reviews", "/api/v1/catalog/items/{itemId}/reviews", "/api/v1/merchants/{merchantId}/reviews", "/api/v1/me/notifications", "/api/v1/me/notifications/{notificationId}/mark-read", "/api/v1/me/notifications/mark-all-read", "/api/v1/me/events");
        assertThat(paths.path("/api/v1/me/reviews").path("post").path("parameters").findValuesAsText("name")).contains("Idempotency-Key");
        assertThat(paths.path("/api/v1/me/reviews").path("post").path("parameters").findValues("required").stream().anyMatch(JsonNode::asBoolean)).isTrue();
        assertThat(paths.path("/api/v1/me/events").path("get").path("responses").path("200").path("content").has("text/event-stream")).isTrue();
        assertThat(document.path("components").path("schemas").path("NotificationResponse").toString()).contains("type", "data", "read", "createdAt");
    }
}
