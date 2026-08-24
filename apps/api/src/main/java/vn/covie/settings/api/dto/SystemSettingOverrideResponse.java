package vn.covie.settings.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;

public record SystemSettingOverrideResponse(
        UUID id,
        SettingScopeType scopeType,
        UUID scopeId,
        JsonNode value,
        long version,
        Instant updatedAt) {
    public static SystemSettingOverrideResponse from(SystemSettingEntity entity) {
        UUID scopeId = switch (entity.getScopeType()) {
            case GLOBAL -> null;
            case MERCHANT -> entity.getMerchantProfileId();
            case CATEGORY -> entity.getCategoryId();
            case LOCATION_AREA -> entity.getLocationAreaId();
        };
        return new SystemSettingOverrideResponse(
                entity.getId(), entity.getScopeType(), scopeId, entity.getValueJson(), entity.getVersion(), entity.getUpdatedAt());
    }
}
