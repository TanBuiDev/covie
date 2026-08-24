package vn.covie.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SettingValueType;

public record SystemSettingDefinition(String key, SettingValueType valueType, JsonNode defaultValue,
        Set<SettingScopeType> allowedScopes, List<SettingScopeType> resolutionOrder, Predicate<JsonNode> validator) {
    public SystemSettingDefinition {
        allowedScopes = Set.copyOf(allowedScopes);
        resolutionOrder = List.copyOf(resolutionOrder);
    }
}
