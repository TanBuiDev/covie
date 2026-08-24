package vn.covie.moderation.api.dto;
import java.time.Instant; import java.util.UUID; import vn.covie.moderation.model.*;
public record ModerationCaseSummaryResponse(UUID id,ModerationCaseType caseType,ModerationCaseStatus status,ModerationTargetType targetType,UUID targetId,String reasonCode,Instant openedAt,Instant resolvedAt) {}
