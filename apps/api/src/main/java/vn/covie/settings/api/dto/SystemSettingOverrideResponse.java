package vn.covie.settings.api.dto;
import com.fasterxml.jackson.databind.JsonNode; import java.time.Instant; import java.util.UUID; import vn.covie.settings.model.*;
public record SystemSettingOverrideResponse(UUID id,SettingScopeType scopeType,UUID scopeId,JsonNode value,long version,Instant updatedAt) {}
