package vn.covie.catalog.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.api.dto.CatalogItemManagementSummaryResponse;
import vn.covie.catalog.api.dto.CatalogItemManagementResponse;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;

@Service
public class CatalogManagementQueryService {
    private final MerchantAccessPolicy access;
    private final CatalogItemRepository items;
    private final VariantRepository variants;
    private final LocationVariantRepository offers;
    private final VariantOptionValueRepository variantOptions;
    private final CatalogItemMediaRepository media;

    public CatalogManagementQueryService(MerchantAccessPolicy access, CatalogItemRepository items,
            VariantRepository variants, LocationVariantRepository offers,
            VariantOptionValueRepository variantOptions, CatalogItemMediaRepository media) {
        this.access = access;
        this.items = items;
        this.variants = variants;
        this.offers = offers;
        this.variantOptions = variantOptions;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public CatalogItemManagementResponse get(UUID merchantId, UUID itemId) {
        access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        CatalogItemEntity item = items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId)
                .orElseThrow(CatalogManagementQueryService::notFound);
        var variantDtos = variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId).stream()
                .map(variant -> new CatalogItemManagementResponse.VariantResponse(
                        variant.getId(), variant.getSku(), variant.getVariantName(), variant.isDefaultVariant(),
                        variant.getVariantStatus(), variantOptions.findAllByIdVariantId(variant.getId()).stream()
                                .map(value -> value.getId().getOptionValueId()).toList(), variant.getVersion()))
                .toList();
        var offerDtos = offers.findAllActiveRecordsByCatalogItemId(itemId).stream()
                .map(offer -> new CatalogItemManagementResponse.LocationOfferResponse(
                        offer.getId(), offer.getMerchantLocationId(), offer.getVariantId(), offer.getPrice(),
                        offer.getCurrencyCode(), offer.isAvailable(), offer.getInventoryMode(),
                        offer.getPreparationTimeMinutes(), offer.getVersion()))
                .toList();
        var mediaIds = media.findAllMediaAssetsByCatalogItemId(itemId).stream().map(value -> value.getId()).toList();
        return new CatalogItemManagementResponse(item.getId(), item.getItemType(), item.getListingKind(),
                item.getPrimaryCategoryId(), item.getName(), item.getDescription(), item.getListingStatus(),
                item.getModerationStatus(), mediaIds, variantDtos, offerDtos, lifecycle(item), item.getVersion());
    }

    private static CatalogItemManagementResponse.LifecycleCapabilities lifecycle(CatalogItemEntity item) {
        boolean visible = item.getModerationStatus() == CatalogModerationStatus.NOT_REQUIRED
                || item.getModerationStatus() == CatalogModerationStatus.APPROVED;
        return new CatalogItemManagementResponse.LifecycleCapabilities(
                item.getListingStatus() == ListingStatus.DRAFT
                        && item.getModerationStatus() != CatalogModerationStatus.PENDING,
                item.getListingStatus() == ListingStatus.ACTIVE,
                item.getListingStatus() == ListingStatus.PAUSED && visible,
                item.getListingStatus() != ListingStatus.DELETED);
    }

    private static vn.covie.shared.web.ApiException notFound() {
        return new vn.covie.shared.web.ApiException(vn.covie.shared.web.ErrorCode.RESOURCE_NOT_FOUND,
                org.springframework.http.HttpStatus.NOT_FOUND, "Resource not found.");
    }

    @Transactional(readOnly = true)
    public List<CatalogItemManagementSummaryResponse> list(UUID merchantId) {
        access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        return items.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchantId).stream()
                .map(CatalogManagementQueryService::summary)
                .toList();
    }

    private static CatalogItemManagementSummaryResponse summary(CatalogItemEntity item) {
        return new CatalogItemManagementSummaryResponse(item.getId(), item.getItemType(), item.getListingKind(),
                item.getName(), item.getListingStatus(), item.getVersion());
    }
}
