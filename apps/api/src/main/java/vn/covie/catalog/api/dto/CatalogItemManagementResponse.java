package vn.covie.catalog.api.dto;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;

public record CatalogItemManagementResponse(UUID id, CatalogItemType type, ListingKind listingKind, UUID categoryId,
        String name, String description, ListingStatus listingStatus, List<VariantResponse> variants,
        List<LocationOfferResponse> locationOffers, long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) String socialCaption) {
    public record VariantResponse(UUID id, String name, boolean isDefault, List<UUID> selectedOptionIds) {}
    public record LocationOfferResponse(UUID id, UUID merchantLocationId, UUID variantId, long price, String currency,
            boolean available, InventoryMode inventoryMode, Integer preparationTimeMinutes) {}
}
