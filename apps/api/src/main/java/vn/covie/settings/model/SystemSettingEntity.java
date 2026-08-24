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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "system_settings")
public class SystemSettingEntity {
    @Id private UUID id;
    @Column(name = "setting_key", nullable = false, length = 160) private String settingKey;
    @Enumerated(EnumType.STRING) @Column(name = "value_type", nullable = false, length = 30) private SettingValueType valueType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "value_json", nullable = false, columnDefinition = "jsonb") private JsonNode valueJson;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 30) private SettingScopeType scopeType;
    @Column(name = "location_area_id") private UUID locationAreaId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "merchant_profile_id") private UUID merchantProfileId;
    @Version @Column(nullable = false) private long version;
    @Column(name = "updated_by_user_id", nullable = false) private UUID updatedByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SystemSettingEntity() {}

    public static SystemSettingEntity create(
            UUID id,
            String settingKey,
            SettingValueType valueType,
            JsonNode valueJson,
            SettingScopeType scopeType,
            UUID scopeId,
            UUID actorId,
            Instant now) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(settingKey);
        Objects.requireNonNull(valueType);
        Objects.requireNonNull(valueJson);
        Objects.requireNonNull(scopeType);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.id = id;
        entity.settingKey = settingKey;
        entity.valueType = valueType;
        entity.valueJson = valueJson.deepCopy();
        entity.scopeType = scopeType;
        switch (scopeType) {
            case GLOBAL -> {
                if (scopeId != null) throw new IllegalArgumentException("GLOBAL scope cannot have scopeId.");
            }
            case LOCATION_AREA -> entity.locationAreaId = Objects.requireNonNull(scopeId, "scopeId");
            case CATEGORY -> entity.categoryId = Objects.requireNonNull(scopeId, "scopeId");
            case MERCHANT -> entity.merchantProfileId = Objects.requireNonNull(scopeId, "scopeId");
        }
        entity.updatedByUserId = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void replaceValue(JsonNode value, UUID actorId, Instant now) {
        this.valueJson = Objects.requireNonNull(value).deepCopy();
        this.updatedByUserId = Objects.requireNonNull(actorId);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public UUID getId() { return id; }
    public String getSettingKey() { return settingKey; }
    public SettingValueType getValueType() { return valueType; }
    public JsonNode getValueJson() { return valueJson; }
    public SettingScopeType getScopeType() { return scopeType; }
    public UUID getLocationAreaId() { return locationAreaId; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public long getVersion() { return version; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
