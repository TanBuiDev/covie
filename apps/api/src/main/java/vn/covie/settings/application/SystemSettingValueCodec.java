package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public class SystemSettingValueCodec {
    public boolean booleanValue(String key, JsonNode value) { if (value == null || !value.isBoolean()) throw invalid(key); return value.booleanValue(); }
    public int integerValue(String key, JsonNode value) { if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw invalid(key); return value.intValue(); }
    public String enumValue(String key, JsonNode value) { if (value == null || !value.isTextual()) throw invalid(key); return value.textValue(); }
    public JsonNode encodeBoolean(boolean value) { return JsonNodeFactory.instance.booleanNode(value); }
    public JsonNode encodeInteger(int value) { return JsonNodeFactory.instance.numberNode(value); }
    public JsonNode encodeString(String value) { return JsonNodeFactory.instance.textNode(value); }
    private static ApiException invalid(String key) { return new ApiException(ErrorCode.SYSTEM_SETTING_VALUE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "System setting value is invalid.", Map.of("key", key)); }
}
