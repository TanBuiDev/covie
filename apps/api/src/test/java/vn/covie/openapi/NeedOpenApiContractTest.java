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
class NeedOpenApiContractTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void documentsPlanSixNeedAndCheckoutContracts() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");
        assertThat(paths.fieldNames()).toIterable().contains(
                "/api/v1/me/need-requests",
                "/api/v1/me/need-requests/{needRequestId}",
                "/api/v1/me/need-requests/{needRequestId}/cancel",
                "/api/v1/me/need-requests/{needRequestId}/responses",
                "/api/v1/merchants/{merchantId}/need-requests",
                "/api/v1/need-requests/{needRequestId}/responses",
                "/api/v1/need-requests/{needRequestId}/responses/{responseId}",
                "/api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw",
                "/api/v1/checkouts/preview",
                "/api/v1/checkouts/commit");

        assertHeader(paths, "/api/v1/me/need-requests", "post", "Idempotency-Key");
        assertHeader(paths, "/api/v1/me/need-requests/{needRequestId}/cancel", "post", "Idempotency-Key");
        assertHeader(paths, "/api/v1/need-requests/{needRequestId}/responses", "post", "Idempotency-Key");
        assertHeader(paths, "/api/v1/need-requests/{needRequestId}/responses/{responseId}", "put", "If-Match");
        assertHeader(paths, "/api/v1/need-requests/{needRequestId}/responses/{responseId}/withdraw", "post", "Idempotency-Key");

        JsonNode sourceType = document.at("/components/schemas/CheckoutPreviewRequest/properties/sourceType/enum");
        assertThat(sourceType).isNotNull();
        assertThat(sourceType.toString()).contains("NEED_REQUEST");
        assertThat(json).contains(
                "CreateNeedRequest",
                "NeedDetailResponse",
                "NeedResponseResponse",
                "MerchantNeedCursorPage",
                "NeedResponseCursorPage");
    }

    private static void assertHeader(JsonNode paths, String path, String method, String headerName) {
        assertThat(paths.path(path).path(method).path("parameters").findValuesAsText("name"))
                .contains(headerName);
    }
}
