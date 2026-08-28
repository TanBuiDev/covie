package vn.covie.catalog.api.dto;

import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.VariantStatus;

public record CatalogItemManagementResponse(UUID id, CatalogItemType type, ListingKind listingKind, UUID categoryId,
        String name, String description, ListingStatus listingStatus, CatalogModerationStatus moderationStatus,
        List<UUID> mediaIds, List<VariantResponse> variants, List<LocationOfferResponse> locationOffers,
        LifecycleCapabilities lifecycle, long version) {
    public record VariantResponse(UUID id, String sku, String name, boolean isDefault, VariantStatus status,
            List<UUID> selectedOptionIds, long version) {}
    public record LocationOfferResponse(UUID id, UUID merchantLocationId, UUID variantId, long price, String currency,
            boolean available, InventoryMode inventoryMode, Integer preparationTimeMinutes, long version) {}
    public record LifecycleCapabilities(
            boolean canPublish, boolean canPause, boolean canActivate, boolean canArchive) {}
}
