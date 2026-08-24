package vn.covie.settings.api.dto;
import com.fasterxml.jackson.databind.JsonNode; import java.time.Instant; import java.util.UUID;
public record SystemSettingHistoryResponse(UUID id,JsonNode oldValue,JsonNode newValue,long oldVersion,long newVersion,UUID changedByUserId,Instant changedAt) {}
