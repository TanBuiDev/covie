package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.settings.model.SettingValueType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class SystemSettingValueCodec {
    private final SystemSettingRegistry registry;

    public SystemSettingValueCodec(SystemSettingRegistry registry) { this.registry = registry; }

    public void validate(String key, JsonNode value) {
        SystemSettingDefinition def = registry.require(key);
        if (!def.validator().test(value)) invalid(key);
    }

    public boolean booleanValue(String key, JsonNode value) {
        requireType(key, SettingValueType.BOOLEAN, value);
        return value.booleanValue();
    }

    public int integerValue(String key, JsonNode value) {
        requireType(key, SettingValueType.INTEGER, value);
        return value.intValue();
    }

    public String enumValue(String key, JsonNode value) {
        requireType(key, SettingValueType.ENUM, value);
        return value.textValue();
    }

    public JsonNode encodeBoolean(boolean value) { return JsonNodeFactory.instance.booleanNode(value); }
    public JsonNode encodeInteger(int value) { return JsonNodeFactory.instance.numberNode(value); }
    public JsonNode encodeString(String value) { return JsonNodeFactory.instance.textNode(value); }

    private void requireType(String key, SettingValueType expected, JsonNode value) {
        SystemSettingDefinition def = registry.require(key);
        if (def.valueType() != expected || !def.validator().test(value)) invalid(key);
    }

    private static void invalid(String key) {
        throw new ApiException(ErrorCode.SYSTEM_SETTING_VALUE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                "System setting value is invalid for " + key + ".");
    }
}
