package vn.covie.settings.api.dto;
import java.util.*; import vn.covie.settings.model.*;
public record SystemSettingDetailResponse(String key,SettingValueType valueType,Object defaultValue,Set<SettingScopeType> allowedScopes,Object effectiveValue,SystemSettingOverrideResponse effectiveSource,List<SystemSettingOverrideResponse> persistedOverrides) {}

