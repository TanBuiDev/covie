package vn.covie.catalog.api.dto.discovery;

import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public record CatalogDiscoveryQuery(
        String q,
        CatalogItemType type,
        ListingKind listingKind,
        UUID categoryId,
        Double lat,
        Double lng,
        UUID addressId,
        Integer radiusMeters,
        Boolean pickup,
        Boolean openNow,
        Long minPrice,
        Long maxPrice,
        CatalogSort sort,
        Integer limit,
        String cursor) {}
