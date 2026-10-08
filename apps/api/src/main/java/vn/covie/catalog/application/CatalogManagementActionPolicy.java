package vn.covie.catalog.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.catalog.api.dto.CatalogItemManagementDetailResponse.ItemAction;
import vn.covie.catalog.api.dto.CatalogItemManagementDetailResponse.OfferAction;
import vn.covie.catalog.api.dto.CatalogItemManagementDetailResponse.VariantAction;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** One source of truth for seller catalog controls and their command preconditions. */
@Component
public class CatalogManagementActionPolicy {
    private final CatalogPublishValidator publishability;
    private final VariantRepository variants;

    public CatalogManagementActionPolicy(CatalogPublishValidator publishability, VariantRepository variants) {
        this.publishability = publishability;
        this.variants = variants;
    }

    public List<ItemAction> itemActions(MerchantAccess access, CatalogItemEntity item) {
        List<ItemAction> result = new ArrayList<>();
        if (MerchantAccessPolicy.allows(access.role(), MerchantCapability.MANAGE_CATALOG_STRUCTURE)) {
            result.add(ItemAction.UPDATE);
            result.add(ItemAction.CREATE_VARIANT);
        }
        if (MerchantAccessPolicy.allows(access.role(), MerchantCapability.PUBLISH_CATALOG)) {
            if (item.getListingStatus() == ListingStatus.DRAFT && publishability.isPublishable(item)) {
                result.add(ItemAction.PUBLISH);
            }
            if (item.getListingStatus() == ListingStatus.ACTIVE) result.add(ItemAction.PAUSE);
            if (item.getListingStatus() == ListingStatus.PAUSED && isVisible(item.getModerationStatus())
                    && publishability.isPublishable(item)) {
                result.add(ItemAction.ACTIVATE);
            }
            if (item.getListingStatus() != ListingStatus.DELETED) result.add(ItemAction.ARCHIVE);
        }
        return List.copyOf(result);
    }

    public List<VariantAction> variantActions(MerchantAccess access, CatalogItemEntity item) {
        if (!MerchantAccessPolicy.allows(access.role(), MerchantCapability.MANAGE_CATALOG_STRUCTURE)) return List.of();
        if (variants.countByCatalogItemIdAndDeletedAtIsNull(item.getId()) > 1) {
            return List.of(VariantAction.UPDATE, VariantAction.DELETE);
        }
        return List.of(VariantAction.UPDATE);
    }

    public List<OfferAction> offerActions(MerchantAccess access, MerchantLocationEntity location) {
        if (location.getDeletedAt() != null
                || !MerchantAccessPolicy.allows(access.role(), MerchantCapability.MANAGE_LOCATION_OFFER)) {
            return List.of();
        }
        return List.of(OfferAction.UPDATE);
    }

    public void requirePublish(CatalogItemEntity item) {
        requireState(item, ListingStatus.DRAFT, "Only draft catalog items can be published.");
        publishability.requirePublishable(item);
    }

    public void requirePause(CatalogItemEntity item) {
        requireState(item, ListingStatus.ACTIVE, "Only active catalog items can be paused.");
    }

    public void requireActivate(CatalogItemEntity item) {
        requireState(item, ListingStatus.PAUSED, "Only paused catalog items can be activated.");
        if (!isVisible(item.getModerationStatus())) throw invalid("Catalog item is not visible after moderation.");
        publishability.requirePublishable(item);
    }

    public void requireArchive(CatalogItemEntity item) {
        if (item.getListingStatus() == ListingStatus.DELETED) throw invalid("Deleted catalog items are terminal.");
    }

    public void requireVariantDelete(CatalogItemEntity item) {
        if (variants.countByCatalogItemIdAndDeletedAtIsNull(item.getId()) <= 1) {
            throw new ApiException(ErrorCode.CATALOG_VARIANT_COMBINATION_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "The final non-deleted variant cannot be deleted.");
        }
    }

    private static boolean isVisible(CatalogModerationStatus status) {
        return status == CatalogModerationStatus.NOT_REQUIRED || status == CatalogModerationStatus.APPROVED;
    }

    private static void requireState(CatalogItemEntity item, ListingStatus expected, String message) {
        if (item.getListingStatus() != expected) throw invalid(message);
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.CATALOG_LIFECYCLE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
