package vn.covie.catalog.api.dto.discovery;

public record CatalogDiscoveryResponse(CursorPageResponse<CatalogItemSummaryResponse> page) {}
