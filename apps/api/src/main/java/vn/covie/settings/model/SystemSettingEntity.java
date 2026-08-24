package vn.covie.settings.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "system_settings")
public class SystemSettingEntity {
    @Id private UUID id;
    @Column(name = "setting_key", nullable = false, length = 160) private String settingKey;
    @Enumerated(EnumType.STRING) @Column(name = "value_type", nullable = false) private SettingValueType valueType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "value_json", nullable = false, columnDefinition = "jsonb") private JsonNode valueJson;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false) private SettingScopeType scopeType;
    @Column(name = "location_area_id") private UUID locationAreaId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "merchant_profile_id") private UUID merchantProfileId;
    @Version private long version;
    @Column(name = "updated_by_user_id", nullable = false) private UUID updatedByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SystemSettingEntity() {}

    public static SystemSettingEntity global(UUID id, String key, SettingValueType type, JsonNode value, UUID actor, Instant now) {
        return create(id, key, type, value, SettingScopeType.GLOBAL, null, null, null, actor, now);
    }

    public static SystemSettingEntity merchant(UUID id, String key, SettingValueType type, JsonNode value, UUID merchantId, UUID actor, Instant now) {
        return create(id, key, type, value, SettingScopeType.MERCHANT, null, null, merchantId, actor, now);
    }

    public static SystemSettingEntity category(UUID id, String key, SettingValueType type, JsonNode value, UUID categoryId, UUID actor, Instant now) {
        return create(id, key, type, value, SettingScopeType.CATEGORY, null, categoryId, null, actor, now);
    }

    public static SystemSettingEntity locationArea(UUID id, String key, SettingValueType type, JsonNode value, UUID areaId, UUID actor, Instant now) {
        return create(id, key, type, value, SettingScopeType.LOCATION_AREA, areaId, null, null, actor, now);
    }

    private static SystemSettingEntity create(UUID id, String key, SettingValueType type, JsonNode value, SettingScopeType scope,
            UUID areaId, UUID categoryId, UUID merchantId, UUID actor, Instant now) {
        SystemSettingEntity setting = new SystemSettingEntity();
        setting.id = id; setting.settingKey = key; setting.valueType = type; setting.valueJson = value; setting.scopeType = scope;
        setting.locationAreaId = areaId; setting.categoryId = categoryId; setting.merchantProfileId = merchantId;
        setting.updatedByUserId = actor; setting.createdAt = now; setting.updatedAt = now;
        return setting;
    }

    public void replaceValue(JsonNode value, UUID actorId, Instant now) { valueJson = value; updatedByUserId = actorId; updatedAt = now; }
    public UUID getId() { return id; } public String getSettingKey() { return settingKey; } public SettingValueType getValueType() { return valueType; }
    public JsonNode getValueJson() { return valueJson; } public SettingScopeType getScopeType() { return scopeType; }
    public UUID getLocationAreaId() { return locationAreaId; } public UUID getCategoryId() { return categoryId; } public UUID getMerchantProfileId() { return merchantProfileId; }
    public long getVersion() { return version; } public UUID getUpdatedByUserId() { return updatedByUserId; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
