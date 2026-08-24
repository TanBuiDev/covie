package vn.covie.settings.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import vn.covie.settings.application.SystemSettingDefinition;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SettingValueType;

public record SystemSettingDefinitionResponse(
        String key,
        SettingValueType valueType,
        JsonNode defaultValue,
        Set<SettingScopeType> allowedScopes) {
    public static SystemSettingDefinitionResponse from(SystemSettingDefinition definition) {
        return new SystemSettingDefinitionResponse(
                definition.key(), definition.valueType(), definition.defaultValue(), definition.allowedScopes());
    }
}
