package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

public record CatalogItemDetailResponse(
        UUID id,
        String slug,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        String description,
        PublicMerchantResponse merchant,
        ResolvedMerchantLocationResponse resolvedLocation,
        List<PublicMediaResponse> media,
        List<PublicOptionGroupResponse> options,
        List<PublicVariantResponse> variants) {}
