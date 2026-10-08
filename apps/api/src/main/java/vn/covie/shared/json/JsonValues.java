package vn.covie.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Bridges persisted Jackson 2 JSON trees to values that Jackson 3 can serialize correctly. */
public final class JsonValues {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonValues() {}

    public static Object unwrap(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.doubleValue();
        if (node.isTextual()) return node.textValue();
        try {
            return JSON.treeToValue(node, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Stored JSON value could not be converted.", exception);
        }
    }
}
