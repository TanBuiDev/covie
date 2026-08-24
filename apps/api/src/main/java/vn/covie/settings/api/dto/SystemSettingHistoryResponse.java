package vn.covie.settings.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import vn.covie.settings.model.SystemSettingHistoryEntity;

public record SystemSettingHistoryResponse(
        UUID id,
        JsonNode oldValue,
        JsonNode newValue,
        long oldVersion,
        long newVersion,
        UUID changedByUserId,
        Instant changedAt) {
    public static SystemSettingHistoryResponse from(SystemSettingHistoryEntity entity) {
        return new SystemSettingHistoryResponse(
                entity.getId(), entity.getOldValueJson(), entity.getNewValueJson(),
                entity.getOldVersion(), entity.getNewVersion(), entity.getChangedByUserId(), entity.getChangedAt());
    }
}
