package vn.covie.settings.api.dto;
import java.time.Instant; import java.util.UUID;
public record SystemSettingHistoryResponse(UUID id,Object oldValue,Object newValue,long oldVersion,long newVersion,UUID changedByUserId,Instant changedAt) {}
