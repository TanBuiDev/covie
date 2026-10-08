package vn.covie.catalog.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CategoryAttributeEntity;
import vn.covie.catalog.model.CategoryStatus;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemAttributeValueRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CategoryAttributeRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.media.model.MediaStatus;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Rechecks seller-visible catalog invariants immediately before publication and reactivation. */
@Component
public class CatalogPublishValidator {
    private final MerchantProfileRepository merchants;
    private final CategoryRepository categories;
    private final CategoryAttributeRepository categoryAttributes;
    private final CatalogItemAttributeValueRepository attributeValues;
    private final VariantRepository variants;
    private final LocationVariantRepository offers;
    private final MerchantLocationRepository locations;
    private final CatalogItemMediaRepository media;

    public CatalogPublishValidator(MerchantProfileRepository merchants, CategoryRepository categories,
            CategoryAttributeRepository categoryAttributes, CatalogItemAttributeValueRepository attributeValues,
            VariantRepository variants, LocationVariantRepository offers, MerchantLocationRepository locations,
            CatalogItemMediaRepository media) {
        this.merchants = merchants; this.categories = categories; this.categoryAttributes = categoryAttributes;
        this.attributeValues = attributeValues; this.variants = variants; this.offers = offers;
        this.locations = locations; this.media = media;
    }

    public void requirePublishable(CatalogItemEntity item) {
        if (item.getDeletedAt() != null) throw invalid(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Catalog item was not found.");
        var merchant = merchants.findByIdAndDeletedAtIsNull(item.getMerchantProfileId())
                .orElseThrow(() -> invalid(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant was not found."));
        if (merchant.getMerchantStatus() != MerchantStatus.ACTIVE)
            throw invalid(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY, "Merchant must be active to publish catalog items.");
        if (merchant.getModerationStatus() != MerchantModerationStatus.NOT_REQUIRED && merchant.getModerationStatus() != MerchantModerationStatus.APPROVED)
            throw invalid(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY, "Merchant is not publicly visible.");
        var category = categories.findByIdAndStatusAndDeletedAtIsNull(item.getPrimaryCategoryId(), CategoryStatus.ACTIVE)
                .orElseThrow(() -> invalid(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Category was not found."));
        if (!category.getCatalogType().supports(item.getItemType()))
            throw invalid(ErrorCode.CATEGORY_NOT_COMPATIBLE, HttpStatus.UNPROCESSABLE_ENTITY, "Category does not support the catalog item type.");

        List<CategoryAttributeEntity> required = categoryAttributes.findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(item.getPrimaryCategoryId())
                .stream().filter(CategoryAttributeEntity::isRequired).toList();
        var supplied = attributeValues.findAllByCatalogItemId(item.getId()).stream()
                .map(value -> value.getCategoryAttributeId()).collect(java.util.stream.Collectors.toSet());
        if (required.stream().anyMatch(attribute -> !supplied.contains(attribute.getId())))
            throw invalid(ErrorCode.CATALOG_ATTRIBUTE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Required category attribute is missing.");

        var itemVariants = variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(item.getId());
        if (itemVariants.stream().noneMatch(variant -> variant.getVariantStatus() == VariantStatus.ACTIVE))
            throw invalid(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "At least one active variant is required.");
        var itemOffers = offers.findAllActiveRecordsByCatalogItemId(item.getId());
        if (itemOffers.stream().noneMatch(v -> v.isAvailable()))
            throw invalid(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "At least one available location offer is required.");
        Map<UUID, vn.covie.merchants.model.MerchantLocationEntity> locationsById = new HashMap<>();
        for (var location : locations.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(item.getMerchantProfileId()))
            locationsById.put(location.getId(), location);
        for (var offer : itemOffers) {
            var location = locationsById.get(offer.getMerchantLocationId());
            if (location == null || location.getLocationStatus() != MerchantLocationStatus.ACTIVE)
                throw invalid(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Every offer location must be active.");
            if (item.getItemType().name().equals("SERVICE") && !location.isServiceEnabled())
                throw invalid(ErrorCode.CATALOG_LOCATION_OFFER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Service offers require a service-enabled location.");
        }
        if (media.findAllMediaAssetsByCatalogItemId(item.getId()).stream().anyMatch(asset -> asset.getMediaStatus() != MediaStatus.READY))
            throw invalid(ErrorCode.MEDIA_NOT_ATTACHABLE, HttpStatus.UNPROCESSABLE_ENTITY, "All attached media must be ready.");
    }

    public boolean isPublishable(CatalogItemEntity item) {
        try {
            requirePublishable(item);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private static ApiException invalid(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message);
    }
}
