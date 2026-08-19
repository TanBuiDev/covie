package vn.covie.catalog.api.dto.discovery;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, boolean hasMore, String nextCursor) {}
