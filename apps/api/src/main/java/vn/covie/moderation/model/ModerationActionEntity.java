package vn.covie.moderation.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "moderation_actions")
public class ModerationActionEntity {
    @Id private UUID id;
    @Column(name = "moderation_case_id", nullable = false) private UUID moderationCaseId;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 30) private ModerationActionType actionType;
    @Column(name = "actor_admin_user_id", nullable = false) private UUID actorAdminUserId;
    @Column(name = "reason_code", length = 60) private String reasonCode;
    @Column(columnDefinition = "text") private String note;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode metadata;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ModerationActionEntity() {}

    public static ModerationActionEntity create(
            UUID id, UUID caseId, ModerationActionType actionType, UUID actorId,
            String reasonCode, String note, JsonNode metadata, Instant now) {
        ModerationActionEntity entity = new ModerationActionEntity();
        entity.id = id;
        entity.moderationCaseId = caseId;
        entity.actionType = actionType;
        entity.actorAdminUserId = actorId;
        entity.reasonCode = normalize(reasonCode);
        entity.note = normalize(note);
        entity.metadata = metadata == null ? null : metadata.deepCopy();
        entity.createdAt = now;
        return entity;
    }

    private static String normalize(String value) { return value == null || value.strip().isEmpty() ? null : value.strip(); }
    public UUID getId() { return id; }
    public UUID getModerationCaseId() { return moderationCaseId; }
    public ModerationActionType getActionType() { return actionType; }
    public UUID getActorAdminUserId() { return actorAdminUserId; }
    public String getReasonCode() { return reasonCode; }
    public String getNote() { return note; }
    public JsonNode getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
