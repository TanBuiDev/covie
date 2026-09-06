package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogResponse(UUID id, UUID actorUserId, String actorType, String eventCategory, String action,
        String entityType, UUID entityId, Object metadata, String requestId, Instant createdAt) {}

