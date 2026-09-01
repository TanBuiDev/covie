package vn.covie.settings.api.dto;
import java.util.List;
import java.util.Set;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SettingValueType;
public record SystemSettingDefinitionResponse(String key, SettingValueType valueType, Object defaultValue, Set<SettingScopeType> allowedScopes, List<SettingScopeType> resolutionOrder) {}
