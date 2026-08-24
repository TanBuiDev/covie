package vn.covie.moderation.api.dto;
import java.time.Instant;import java.util.UUID;import vn.covie.moderation.model.ModerationActionType;
public record ModerationActionResponse(UUID id,ModerationActionType action,UUID actorAdminUserId,String reasonCode,String note,Instant createdAt) {}
