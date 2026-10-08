package vn.covie.catalog.api.dto.publicview;

import vn.covie.catalog.api.dto.discovery.CursorPageResponse;

public record PublicMerchantCatalogResponse(CursorPageResponse<PublicMerchantCatalogItemResponse> page) {}
