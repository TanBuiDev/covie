package vn.covie.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

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
                "/api/v1/merchants/{merchantId}/locations",
                "/api/v1/media/uploads",
                "/api/v1/media/{mediaId}/complete",
                "/api/v1/merchants/{merchantId}/catalog-items",
                "/api/v1/catalog/items",
                "/api/v1/catalog/items/{itemId}");
        assertThat(json).contains("/api/v1/health");
        for (String command : new String[] {"ratings", "reputation"}) {
            JsonNode parameters=paths.path("/api/v1/admin/maintenance/"+command+"/rebuild").path("post").path("parameters");
            int requiredHeaders=0;
            for (JsonNode parameter : parameters) {
                if (parameter.path("name").asText().equals("Idempotency-Key")
                        || parameter.path("name").asText().equals("X-Admin-Reason")) {
                    assertThat(parameter.path("required").asBoolean()).as("Maintenance %s header %s",command,parameter.path("name").asText()).isTrue();
                    requiredHeaders++;
                }
            }
            assertThat(requiredHeaders).isEqualTo(2);
        }
        assertThat(paths.path("/api/v1/auth/google/status").path("get").isMissingNode()).isFalse();
        String googleStatus = mockMvc.perform(get("/api/v1/auth/google/status"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(googleStatus).size()).isEqualTo(1);
        assertThat(objectMapper.readTree(googleStatus).path("configured").isBoolean()).isTrue();

        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/catalog-items", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/confirm", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/mark-delivered", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/orders/{orderId}/report-delivery-issue", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/media/{mediaId}/complete", "post", "Idempotency-Key");
        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/locations/{locationId}", "patch", "If-Match");
        assertHeaderParameter(paths, "/api/v1/merchants/{merchantId}/catalog-items/{itemId}", "patch", "If-Match");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}", "get", "ETag");
        assertHeaderResponse(paths, "/api/v1/merchants/{merchantId}/locations/{locationId}", "get", "ETag");
        JsonNode preparationTime = document.path("components").path("schemas")
                .path("UpdateLocationOfferRequest").path("properties").path("preparationTimeMinutes");
        assertThat(preparationTime.isMissingNode()).isFalse();
        assertThat(preparationTime.path("nullable").asBoolean()
                || preparationTime.path("type").toString().contains("null")
                || preparationTime.path("anyOf").toString().contains("\"type\":\"null\""))
                .as("preparationTimeMinutes schema: %s", preparationTime).isTrue();
        assertEligibleNeedItemsContract(document);
        JsonNode needAllowedActions = document.path("components").path("schemas")
                .path("NeedDetailResponse").path("properties").path("allowedActions");
        assertThat(needAllowedActions.path("type").asText()).isEqualTo("array");
        assertThat(needAllowedActions.path("items").path("enum").toString()).contains("\"CANCEL\"");
        String output = System.getProperty("covie.openapi.output", "target/openapi/openapi.json");
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, objectMapper.writeValueAsString(canonicalize(document)));
    }

    // Reflection can enumerate Spring Page fields differently across JVMs.
    // Sort object keys only: array ordering and all schema values are preserved.
    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            var result = objectMapper.createObjectNode();
            var names = new java.util.ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            names.forEach(name -> result.set(name, canonicalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    @Test
    void canonicalExportSortsNestedKeysAndPreservesArrayOrder() throws Exception {
        var input = objectMapper.readTree("{\"z\":[{\"z\":1,\"a\":2},3,1],\"a\":true}");
        assertThat(objectMapper.writeValueAsString(canonicalize(input)))
                .isEqualTo("{\"a\":true,\"z\":[{\"a\":2,\"z\":1},3,1]}");
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

    private void assertEligibleNeedItemsContract(JsonNode document) {
        JsonNode operation = document.path("paths")
                .path("/api/v1/merchants/{merchantId}/need-requests/{needRequestId}/eligible-items")
                .path("get");
        assertThat(operation.isMissingNode()).as("merchant Need eligible-items GET").isFalse();
        assertThat(operation.path("operationId").asText()).isEqualTo("eligibleItems");
        assertQueryParameter(operation, "locationId", true, "string");
        assertQueryParameter(operation, "limit", false, "integer");
        assertQueryParameter(operation, "cursor", false, "string");
        assertThat(operation.path("responses").path("200").path("content").path("*/*")
                .path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/NeedEligibleItemCursorPage");

        JsonNode schemas = document.path("components").path("schemas");
        JsonNode page = schemas.path("NeedEligibleItemCursorPage").path("properties");
        assertThat(page.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("items", "hasMore", "nextCursor", "maxResponseItems");
        assertThat(page.path("items").path("type").asText()).isEqualTo("array");
        assertThat(page.path("items").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/NeedEligibleItemResponse");
        assertThat(page.path("hasMore").path("type").asText()).isEqualTo("boolean");
        assertThat(page.path("maxResponseItems").path("type").asText()).isEqualTo("integer");
        JsonNode nextCursor = page.path("nextCursor");
        assertThat(nextCursor.path("nullable").asBoolean()
                || nextCursor.path("type").toString().contains("null")
                || nextCursor.path("anyOf").toString().contains("\"type\":\"null\""))
                .as("nextCursor must allow null on a terminal page: %s", nextCursor).isTrue();

        JsonNode item = schemas.path("NeedEligibleItemResponse").path("properties");
        assertThat(item.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "locationVariantId", "catalogItemId", "variantId", "catalogItemName",
                "variantName", "currentPrice", "available");
        for (String id : new String[] {"locationVariantId", "catalogItemId", "variantId"}) {
            assertThat(item.path(id).path("format").asText()).isEqualTo("uuid");
        }
        assertThat(item.path("currentPrice").path("$ref").asText())
                .isEqualTo("#/components/schemas/MoneyResponse");
        assertThat(item.path("available").path("type").asText()).isEqualTo("boolean");
        JsonNode money = schemas.path("MoneyResponse").path("properties");
        assertThat(money.fieldNames()).toIterable().containsExactlyInAnyOrder("amount", "currency");
        assertThat(money.path("amount").path("type").asText()).isEqualTo("integer");
        assertThat(money.path("currency").path("type").asText()).isEqualTo("string");
    }

    private void assertQueryParameter(JsonNode operation, String name, boolean required, String type) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())
                    && "query".equals(parameter.path("in").asText())) {
                assertThat(parameter.path("required").asBoolean()).as(name + " required").isEqualTo(required);
                assertThat(parameter.path("schema").path("type").asText()).as(name + " type").isEqualTo(type);
                return;
            }
        }
        assertThat(operation.path("parameters").findValuesAsText("name"))
                .as("missing query parameter " + name).contains(name);
    }
}
