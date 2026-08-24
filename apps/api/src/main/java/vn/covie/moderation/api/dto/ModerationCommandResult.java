package vn.covie.moderation.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationCaseStatus;

public record ModerationCommandResult(UUID caseId, ModerationCaseStatus status, ModerationActionType action, Instant resolvedAt) {}
