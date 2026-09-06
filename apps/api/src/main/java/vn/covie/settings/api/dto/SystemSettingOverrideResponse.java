package vn.covie.settings.api.dto;
import java.time.Instant; import java.util.UUID; import vn.covie.settings.model.*;
public record SystemSettingOverrideResponse(UUID id,SettingScopeType scopeType,UUID scopeId,Object value,long version,Instant updatedAt) {}

