package vn.covie.catalog.api.dto;

import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;

/** Lightweight row used by the seller catalog management list. */
public record CatalogItemManagementSummaryResponse(
        UUID id,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        ListingStatus listingStatus,
        long version) {}
