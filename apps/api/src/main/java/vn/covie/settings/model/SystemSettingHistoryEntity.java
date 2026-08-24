package vn.covie.settings.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "system_setting_history")
public class SystemSettingHistoryEntity {
    @Id private UUID id;
    @Column(name = "setting_id", nullable = false) private UUID settingId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "old_value_json", columnDefinition = "jsonb") private JsonNode oldValueJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_value_json", nullable = false, columnDefinition = "jsonb") private JsonNode newValueJson;
    @Column(name = "old_version", nullable = false) private long oldVersion;
    @Column(name = "new_version", nullable = false) private long newVersion;
    @Column(name = "changed_by_user_id", nullable = false) private UUID changedByUserId;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;
    protected SystemSettingHistoryEntity() {}
    public static SystemSettingHistoryEntity create(UUID id, UUID settingId, JsonNode oldValue, JsonNode newValue, long oldVersion, long newVersion, UUID actor, Instant now) {
        SystemSettingHistoryEntity history = new SystemSettingHistoryEntity(); history.id = id; history.settingId = settingId; history.oldValueJson = oldValue; history.newValueJson = newValue; history.oldVersion = oldVersion; history.newVersion = newVersion; history.changedByUserId = actor; history.changedAt = now; return history;
    }
    public UUID getId() { return id; } public UUID getSettingId() { return settingId; } public JsonNode getOldValueJson() { return oldValueJson; } public JsonNode getNewValueJson() { return newValueJson; } public long getOldVersion() { return oldVersion; } public long getNewVersion() { return newVersion; } public UUID getChangedByUserId() { return changedByUserId; } public Instant getChangedAt() { return changedAt; }
}
