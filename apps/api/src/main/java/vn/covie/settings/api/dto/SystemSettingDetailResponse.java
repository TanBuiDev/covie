package vn.covie.settings.api.dto;
import com.fasterxml.jackson.databind.JsonNode; import java.util.*; import vn.covie.settings.model.*;
public record SystemSettingDetailResponse(String key,SettingValueType valueType,JsonNode defaultValue,Set<SettingScopeType> allowedScopes,JsonNode effectiveValue,SystemSettingOverrideResponse effectiveSource,List<SystemSettingOverrideResponse> persistedOverrides) {}
