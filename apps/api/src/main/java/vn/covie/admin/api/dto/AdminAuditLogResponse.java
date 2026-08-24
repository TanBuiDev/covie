package vn.covie.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogResponse(UUID id, UUID actorUserId, String actorType, String eventCategory, String action,
        String entityType, UUID entityId, JsonNode metadata, String requestId, Instant createdAt) {}
