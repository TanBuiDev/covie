package vn.covie.moderation.api.dto;
import java.util.List;
public record ModerationCaseDetailResponse(ModerationCaseSummaryResponse moderationCase,List<ModerationActionResponse> actions) {}
