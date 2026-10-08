package vn.covie.catalog.api.dto.publicview;

import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.shared.money.Money;

public record PublicMerchantCatalogItemResponse(
        UUID id,
        CatalogItemType type,
        ListingKind listingKind,
        String name,
        String slug,
        PublicMediaResponse primaryMedia,
        Money priceFrom) {}
