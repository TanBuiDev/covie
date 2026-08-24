package vn.covie.moderation.api.dto;
import java.util.UUID;
import vn.covie.moderation.model.ModerationCaseStatus;
public record ReportCreatedResponse(UUID caseId, ModerationCaseStatus status) {}
