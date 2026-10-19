package vn.covie.admin.api.dto;

import java.util.List;
import vn.covie.moderation.api.dto.ModerationCaseSummaryResponse;

public record AdminCatalogCasePageResponse(List<ModerationCaseSummaryResponse> items,
        String nextCursor, boolean hasMore, long total) {}
