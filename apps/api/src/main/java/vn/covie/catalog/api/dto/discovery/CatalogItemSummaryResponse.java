package vn.covie.catalog.api.dto.discovery;

import java.util.UUID;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantSummary;
import vn.covie.catalog.api.dto.publicview.ResolvedMerchantLocationResponse;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.shared.money.Money;

public record CatalogItemSummaryResponse(
        UUID id,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        String slug,
        PublicMediaResponse primaryMedia,
        PublicMerchantSummary merchant,
        ResolvedMerchantLocationResponse resolvedLocation,
        Money priceFrom,
        CatalogAvailability availability) {}
