package vn.covie.catalog.api.dto;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.media.model.MediaStatus;

/** Complete seller-owned read model for granular, versioned catalog edits. */
public record CatalogItemManagementDetailResponse(
        UUID id,
        CatalogItemType type,
        ListingKind listingKind,
        UUID categoryId,
        String name,
        String description,
        ListingStatus listingStatus,
        CatalogModerationStatus moderationStatus,
        long version,
        List<ItemAction> allowedActions,
        List<CatalogManagementAttributeResponse> attributes,
        List<String> tags,
        List<CatalogManagementMediaResponse> media,
        List<CatalogManagementOptionGroupResponse> optionGroups,
        List<CatalogManagementVariantResponse> variants,
        List<CatalogManagementLocationOfferResponse> locationOffers) {

    public enum ItemAction { UPDATE, CREATE_VARIANT, PUBLISH, PAUSE, ACTIVATE, ARCHIVE }
    public enum VariantAction { UPDATE, DELETE }
    public enum OfferAction { UPDATE }

    public record CatalogManagementAttributeResponse(UUID categoryAttributeId, String attributeKey, String valueText,
            BigDecimal valueNumber, Boolean valueBoolean, UUID optionId) {}

    public record CatalogManagementMediaResponse(UUID id, String contentType, MediaStatus status, URI url, int sortOrder,
            boolean primary) {}

    public record CatalogManagementOptionGroupResponse(UUID id, String name, int sortOrder,
            List<CatalogManagementOptionValueResponse> values) {}
    public record CatalogManagementOptionValueResponse(UUID id, String value, int sortOrder) {}

    public record CatalogManagementVariantResponse(UUID id, String sku, String name, boolean defaultVariant, VariantStatus status,
            int sortOrder, List<UUID> selectedOptionIds, long version, List<VariantAction> allowedActions) {}

    public record CatalogManagementLocationOfferResponse(UUID id, UUID merchantLocationId, String merchantLocationName,
            UUID variantId, long price, String currency, boolean available, InventoryMode inventoryMode,
            Integer preparationTimeMinutes, long version, List<OfferAction> allowedActions) {}
}
