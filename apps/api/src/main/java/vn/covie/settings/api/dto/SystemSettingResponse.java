package vn.covie.settings.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SettingValueType;

/** Public representation of a persisted setting override. */
public record SystemSettingResponse(
        UUID id,
        String key,
        SettingValueType valueType,
        Object value,
        SettingScopeType scopeType,
        UUID scopeId,
        long version,
        UUID updatedByUserId,
        Instant updatedAt) {}
