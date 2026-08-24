package vn.covie.needs.api.dto;

import java.util.List;

public record NeedResponseCursorPage(List<NeedResponseResponse> items, boolean hasMore, String nextCursor) {}
