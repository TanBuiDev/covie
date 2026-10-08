package vn.covie.needs.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.delivery.model.CoverageType;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.moderation.model.ContentModerationStatus;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.CategoryHierarchyRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedResponseEligibilityPolicy {
    private final MerchantAccessPolicy merchantAccess;
    private final MerchantLocationRepository locations;
    private final LocationVariantRepository locationVariants;
    private final VariantRepository variants;
    private final CatalogItemRepository catalogItems;
    private final CategoryHierarchyRepository categories;
    private final InventoryBalanceRepository inventory;
    private final NeedPolicyProvider policies;
    private final NeedLocationContextFactory needLocations;
    private final DeliveryCoverageEvaluator coverage;

    public NeedResponseEligibilityPolicy(
            MerchantAccessPolicy merchantAccess,
            MerchantLocationRepository locations,
            LocationVariantRepository locationVariants,
            VariantRepository variants,
            CatalogItemRepository catalogItems,
            CategoryHierarchyRepository categories,
            InventoryBalanceRepository inventory,
            NeedPolicyProvider policies,
            NeedLocationContextFactory needLocations,
            DeliveryCoverageEvaluator coverage) {
        this.merchantAccess = merchantAccess;
        this.locations = locations;
        this.locationVariants = locationVariants;
        this.variants = variants;
        this.catalogItems = catalogItems;
        this.categories = categories;
        this.inventory = inventory;
        this.policies = policies;
        this.needLocations = needLocations;
        this.coverage = coverage;
    }

    /** Command callers hold the NeedRequest row lock; a read can reuse these predicates only as advisory state. */
    public List<EligibleNeedResponseItem> validate(
            NeedRequestEntity need, UUID merchantId, UUID merchantLocationId,
            List<UUID> requestedLocationVariantIds, Instant now) {
        if (!need.isActiveAt(now)) {
            throw conflict(ErrorCode.NEED_NOT_ACTIVE, "Need is not active.");
        }
        if (need.getModerationStatus() != ContentModerationStatus.NOT_REQUIRED
                && need.getModerationStatus() != ContentModerationStatus.APPROVED) throw notFound();
        if (requestedLocationVariantIds == null || requestedLocationVariantIds.isEmpty()) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "At least one response item is required.");
        }

        merchantAccess.requireLocation(merchantId, merchantLocationId, MerchantCapability.RESPOND_TO_NEEDS);
        MerchantLocationEntity location = locations
                .findByIdAndMerchantProfileIdAndDeletedAtIsNull(merchantLocationId, merchantId)
                .orElseThrow(NeedResponseEligibilityPolicy::notFound);
        if (location.getLocationStatus() != MerchantLocationStatus.ACTIVE) throw notFound();

        NeedPolicySnapshot policy = policies.resolve(merchantId, need.getCategoryId(), need.getLocationAreaId());
        if (requestedLocationVariantIds.size() > policy.maxResponseItems()) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_LIMIT_EXCEEDED,
                    "Response contains more items than the current Need policy allows.");
        }
        Set<UUID> distinct = new HashSet<>(requestedLocationVariantIds);
        if (distinct.size() != requestedLocationVariantIds.size()) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Duplicate location variants are not allowed.");
        }

        ensureServiceable(need, location);
        List<EligibleNeedResponseItem> result = new ArrayList<>(requestedLocationVariantIds.size());
        for (UUID locationVariantId : requestedLocationVariantIds) {
            result.add(validateItem(need, merchantId, merchantLocationId, locationVariantId));
        }
        return List.copyOf(result);
    }

    /** Candidate reads use the same predicates, filtering only item-level invalidity. */
    public Optional<EligibleNeedResponseItem> evaluateForRead(
            NeedRequestEntity need, UUID merchantId, UUID merchantLocationId,
            UUID locationVariantId, Instant now) {
        try {
            return Optional.of(validate(need, merchantId, merchantLocationId,
                    List.of(locationVariantId), now).getFirst());
        } catch (ApiException exception) {
            if (exception.code() == ErrorCode.NEED_RESPONSE_ITEM_INVALID) return Optional.empty();
            throw exception;
        }
    }

    private EligibleNeedResponseItem validateItem(
            NeedRequestEntity need, UUID merchantId, UUID merchantLocationId, UUID locationVariantId) {
        LocationVariantEntity offer = locationVariants.findUsableById(locationVariantId)
                .filter(v -> merchantLocationId.equals(v.getMerchantLocationId()))
                .orElseThrow(() -> invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID,
                        "Response item is not available from the selected merchant location."));
        if (!offer.isAvailable() || offer.getDeletedAt() != null) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Response item is unavailable.");
        }

        VariantEntity variant = variants.findById(offer.getVariantId())
                .filter(v -> v.getDeletedAt() == null && v.getVariantStatus() == VariantStatus.ACTIVE)
                .orElseThrow(() -> invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Variant is unavailable."));
        CatalogItemEntity item = catalogItems.findById(variant.getCatalogItemId())
                .filter(v -> v.getDeletedAt() == null && merchantId.equals(v.getMerchantProfileId()))
                .filter(v -> v.getListingStatus() == ListingStatus.ACTIVE)
                .filter(v -> "NOT_REQUIRED".equals(v.getModerationStatus().name())
                        || "APPROVED".equals(v.getModerationStatus().name()))
                .orElseThrow(() -> invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Catalog item is unavailable."));

        CatalogItemType expectedType = need.getNeedType() == NeedType.PRODUCT
                ? CatalogItemType.PRODUCT : CatalogItemType.SERVICE;
        if (item.getItemType() != expectedType) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Catalog item type does not match the Need.");
        }
        if (!categories.isSameOrDescendant(need.getCategoryId(), item.getPrimaryCategoryId())) {
            throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Catalog item category does not match the Need.");
        }

        Long availableQuantity = null;
        if (need.getNeedType() == NeedType.PRODUCT && offer.getInventoryMode() == InventoryMode.TRACKED) {
            availableQuantity = inventory.findById(offer.getId()).map(v -> v.availableQuantity()).orElse(0L);
            if (availableQuantity <= 0) {
                throw invalid(ErrorCode.NEED_RESPONSE_ITEM_INVALID, "Tracked response item has no available stock.");
            }
        }

        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.put("available", true);
        snapshot.put("inventoryMode", offer.getInventoryMode() == null ? "UNTRACKED" : offer.getInventoryMode().name());
        if (availableQuantity != null) snapshot.put("availableQuantity", availableQuantity);
        if (offer.getPreparationTimeMinutes() != null) {
            snapshot.put("preparationTimeMinutes", offer.getPreparationTimeMinutes());
        }
        return new EligibleNeedResponseItem(item.getId(), variant.getId(), offer.getId(),
                offer.getPrice(), offer.getCurrencyCode(), snapshot);
    }

    private void ensureServiceable(NeedRequestEntity need, MerchantLocationEntity location) {
        long distanceMeters = haversineMeters(
                need.getGeoPoint().getY(), need.getGeoPoint().getX(),
                location.getGeoPoint().getY(), location.getGeoPoint().getX());
        if (distanceMeters > need.getSearchRadiusMeters()) {
            throw invalid(ErrorCode.NEED_RESPONSE_LOCATION_NOT_ELIGIBLE,
                    "Merchant location is outside the Need search radius.");
        }

        boolean eligible;
        if (need.getNeedType() == NeedType.PRODUCT) {
            eligible = location.isPickupEnabled()
                    || (location.isSellerDeliveryEnabled()
                    && coverage.evaluate(location.getId(), CoverageType.PRODUCT_DELIVERY,
                            needLocations.from(need)).eligible());
        } else {
            eligible = location.isServiceEnabled()
                    && coverage.evaluate(location.getId(), CoverageType.SERVICE_AREA,
                            needLocations.from(need)).eligible();
        }
        if (!eligible) {
            throw invalid(ErrorCode.NEED_RESPONSE_LOCATION_NOT_ELIGIBLE,
                    "Merchant location cannot service this Need.");
        }
    }

    private static long haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6_371_008.8d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round(radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static ApiException invalid(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.CONFLICT, message, Map.of());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
