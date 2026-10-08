package vn.covie.needs.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NeedEligibleItemCursorPage(
        List<NeedEligibleItemResponse> items,
        boolean hasMore,
        @Schema(nullable = true, description = "Null when no unscanned candidate rows remain.") String nextCursor,
        int maxResponseItems) {}
