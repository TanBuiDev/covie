package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import vn.covie.catalog.model.CatalogItemType;

public record PublicMerchantCapabilitiesResponse(List<CatalogItemType> catalogTypes) {}
