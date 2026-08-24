package vn.covie.needs.api.dto;

import java.util.List;

public record NeedCursorPage(List<NeedSummaryResponse> items, boolean hasMore, String nextCursor) {}
