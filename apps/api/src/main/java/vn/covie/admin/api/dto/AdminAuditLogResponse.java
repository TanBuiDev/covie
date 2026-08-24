package vn.covie.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import vn.covie.audit.AuditLogEntity;

public record AdminAuditLogResponse(UUID id,UUID actorUserId,String actorType,String eventCategory,String action,String entityType,UUID entityId,JsonNode safeSnapshot,String requestId,Instant createdAt){
    public static AdminAuditLogResponse from(AuditLogEntity e){return new AdminAuditLogResponse(e.getId(),e.getActorUserId(),e.getActorType(),e.getEventCategory(),e.getAction(),e.getEntityType(),e.getEntityId(),e.getAfterSnapshot(),e.getRequestId(),e.getCreatedAt());}
}
