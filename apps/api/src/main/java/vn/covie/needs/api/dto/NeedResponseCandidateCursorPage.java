package vn.covie.needs.api.dto;

import java.util.List;

public record NeedResponseCandidateCursorPage(
        List<NeedResponseCandidateResponse> items,
        boolean hasMore,
        String nextCursor,
        int maxItems) {}
