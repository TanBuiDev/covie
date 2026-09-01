package vn.covie.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class OpenApiExportTest extends PostgresIntegrationTest {
    @Autowired
    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportsOpenApiDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");
        assertThat(paths.fieldNames()).toIterable().contains(
                "/api/v1/me/merchants",
                "/api/v1/me/reputation",
                "/api/v1/merchants/{merchantId}/locations",
                "/api/v1/media/uploads",
                "/api/v1/media/{mediaId}/complete",
                "/api/v1/merchants/{merchantId}/catalog-items",
                "/api/v1/catalog/items",
                "/api/v1/catalog/merchants/{merchantId}/items",
                "/api/v1/catalog/items/{itemId}",
                "/api/v1/need-requests/{needRequestId}/responses/candidates",
                "/api/v1/admin/shops",
                "/api/v1/admin/listings",
                "/api/v1/admin/needs");
        assertThat(json).contains("/api/v1/health");

        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/catalog-items", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/confirm", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/mark-delivered", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/report-delivery-issue", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/media/{mediaId}/complete", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/locations/{locationId}", "patch", "If-Match");
        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/catalog-items/{itemId}", "patch", "If-Match");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}", "get", "ETag");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}/locations/{locationId}", "get", "ETag");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}/catalog-items/{itemId}", "get", "ETag");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}/contacts/{contactId}", "get", "ETag");
        String output = System.getProperty("covie.openapi.output", "target/openapi/openapi.json");
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        normalizeGeneratedPageSchemaOrder(document);
        Files.writeString(outputPath, objectMapper.writeValueAsString(document));
    }

    private void normalizeGeneratedPageSchemaOrder(JsonNode document) {
        JsonNode schemas = document.path("components").path("schemas");
        if (!schemas.isObject()) {
            return;
        }
        schemas.fields().forEachRemaining(entry -> {
            String schemaName = entry.getKey();
            if (schemaName.startsWith("Page") || schemaName.equals("SortObject")) {
                JsonNode properties = entry.getValue().path("properties");
                if (properties instanceof ObjectNode objectProperties) {
                    TreeMap<String, JsonNode> sorted = new TreeMap<>();
                    objectProperties.fields().forEachRemaining(property -> sorted.put(property.getKey(), property.getValue()));
                    objectProperties.removeAll();
                    sorted.forEach(objectProperties::set);
                }
            }
        });
    }

    private void assertHeaderParameter(JsonNode paths, String path, String method, String headerName) {
        assertThat(paths.path(path).path(method).path("parameters").findValuesAsText("name"))
                .contains(headerName);
    }

    private void assertHeaderResponse(JsonNode paths, String path, String method, String headerName) {
        assertThat(paths.path(path).path(method).path("responses").path("200").path("headers").fieldNames())
                .toIterable()
                .contains(headerName);
    }
}
