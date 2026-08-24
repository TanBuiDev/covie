package vn.covie.audit;

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
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id private UUID id;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "actor_type", nullable = false) private String actorType;
    @Column(name = "event_category", nullable = false) private String eventCategory;
    @Column(nullable = false) private String action;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id") private UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_snapshot", columnDefinition = "jsonb") private JsonNode afterSnapshot;
    @Column(name = "request_id") private String requestId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AuditLogEntity() {}
    public static AuditLogEntity security(UUID id, UUID actorUserId, String action, String entityType, UUID entityId, String requestId, JsonNode metadata, Instant now) { AuditLogEntity event = new AuditLogEntity(); event.id=id; event.actorUserId=actorUserId; event.actorType=actorUserId == null ? "ANONYMOUS" : "USER"; event.eventCategory="SECURITY"; event.action=action; event.entityType=entityType; event.entityId=entityId; event.requestId=requestId; event.afterSnapshot=metadata; event.createdAt=now; return event; }
    public UUID getId(){return id;} public UUID getActorUserId(){return actorUserId;} public String getActorType(){return actorType;} public String getEventCategory(){return eventCategory;} public String getAction(){return action;} public String getEntityType(){return entityType;} public UUID getEntityId(){return entityId;} public JsonNode getAfterSnapshot(){return afterSnapshot;} public String getRequestId(){return requestId;} public Instant getCreatedAt(){return createdAt;}
}
