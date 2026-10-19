package vn.covie.catalog.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.billing.application.BillingEntitlementService;
import vn.covie.catalog.api.dto.publicview.CatalogItemDetailResponse;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicOfferResponse;
import vn.covie.catalog.api.dto.publicview.PublicOptionGroupResponse;
import vn.covie.catalog.api.dto.publicview.PublicOptionValueResponse;
import vn.covie.catalog.api.dto.publicview.PublicVariantResponse;
import vn.covie.catalog.api.dto.publicview.ResolvedMerchantLocationResponse;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.OptionGroupEntity;
import vn.covie.catalog.model.OptionValueEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemMediaRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.OptionGroupRepository;
import vn.covie.catalog.persistence.OptionValueRepository;
import vn.covie.catalog.persistence.VariantOptionValueRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationContextResolver;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaStatus;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.shared.money.Money;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogDetailService {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final CatalogItemRepository items;
    private final VariantRepository variants;
    private final LocationVariantRepository offers;
    private final MerchantLocationRepository locations;
    private final CatalogItemMediaRepository itemMedia;
    private final OptionGroupRepository optionGroups;
    private final OptionValueRepository optionValues;
    private final VariantOptionValueRepository variantOptions;
    private final LocationContextResolver contexts;
    private final PublicMerchantViewService merchantViews;
    private final MediaUrlResolver mediaUrls;
    private BillingEntitlementService billing;

    @Autowired public void setBillingEntitlements(BillingEntitlementService billing) {
        this.billing = java.util.Objects.requireNonNull(billing);
    }

    public CatalogDetailService(CatalogItemRepository items, VariantRepository variants,
            LocationVariantRepository offers, MerchantLocationRepository locations,
            CatalogItemMediaRepository itemMedia, OptionGroupRepository optionGroups,
            OptionValueRepository optionValues, VariantOptionValueRepository variantOptions,
            LocationContextResolver contexts, PublicMerchantViewService merchantViews, MediaUrlResolver mediaUrls) {
        this.items = items;
        this.variants = variants;
        this.offers = offers;
        this.locations = locations;
        this.itemMedia = itemMedia;
        this.optionGroups = optionGroups;
        this.optionValues = optionValues;
        this.variantOptions = variantOptions;
        this.contexts = contexts;
        this.merchantViews = merchantViews;
        this.mediaUrls = mediaUrls;
    }

    public CatalogItemDetailResponse get(UUID itemId, UUID locationId, UUID addressId, Double lat, Double lng) {
        CatalogItemEntity item = requireVisibleItem(itemId);
        MerchantProfileEntity merchant = merchantViews.requireVisible(item.getMerchantProfileId());
        if (billing != null) {
            var eligible = billing.evaluate(merchant.getId());
            if (eligible.enforced() && !eligible.canSell()) throw notFound();
        }
        List<VariantEntity> activeVariants = variants.findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(itemId)
                .stream().filter(variant -> variant.getVariantStatus() == VariantStatus.ACTIVE).toList();
        Map<UUID, VariantEntity> variantsById = activeVariants.stream()
                .collect(java.util.stream.Collectors.toMap(VariantEntity::getId, variant -> variant));
        Map<UUID, MerchantLocationEntity> locationsById = merchantLocations(merchant.getId());
        List<LocationVariantEntity> visibleOffers = offers.findAllActiveRecordsByCatalogItemId(itemId).stream()
                .filter(offer -> variantsById.containsKey(offer.getVariantId()))
                .filter(offer -> locationsById.containsKey(offer.getMerchantLocationId()))
                .toList();
        Resolution resolution = resolve(merchant.getId(), locationId, addressId, lat, lng, locationsById, visibleOffers);

        return new CatalogItemDetailResponse(item.getId(), item.getSlug(), item.getItemType(), item.getListingKind(),
                item.getName(), item.getDescription(), merchantViews.toResponse(merchant),
                merchantViews.locationResponse(resolution.location(), resolution.distanceMeters(), resolution.source()),
                media(itemId), optionGroups(itemId), variantResponses(activeVariants, visibleOffers, resolution.location().getId()));
    }

    private CatalogItemEntity requireVisibleItem(UUID itemId) {
        CatalogItemEntity item = items.findById(itemId).orElseThrow(CatalogDetailService::notFound);
        if (item.getDeletedAt() != null || item.getListingStatus() != ListingStatus.ACTIVE
                || (item.getModerationStatus() != CatalogModerationStatus.NOT_REQUIRED
                        && item.getModerationStatus() != CatalogModerationStatus.APPROVED)) {
            throw notFound();
        }
        return item;
    }

    private Map<UUID, MerchantLocationEntity> merchantLocations(UUID merchantId) {
        Map<UUID, MerchantLocationEntity> result = new HashMap<>();
        for (MerchantLocationEntity location : locations.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchantId)) {
            if (location.getLocationStatus() == MerchantLocationStatus.ACTIVE) {
                result.put(location.getId(), location);
            }
        }
        return result;
    }

    private Resolution resolve(UUID merchantId, UUID locationId, UUID addressId, Double lat, Double lng,
            Map<UUID, MerchantLocationEntity> locationsById, List<LocationVariantEntity> visibleOffers) {
        Map<UUID, List<LocationVariantEntity>> offersByLocation = new HashMap<>();
        for (LocationVariantEntity offer : visibleOffers) {
            offersByLocation.computeIfAbsent(offer.getMerchantLocationId(), unused -> new ArrayList<>()).add(offer);
        }
        if (locationId != null) {
            MerchantLocationEntity location = locationsById.get(locationId);
            if (location == null || location.getMerchantProfileId() == null || !location.getMerchantProfileId().equals(merchantId)
                    || !hasAvailableOffer(offersByLocation.get(locationId))) {
                throw notFound();
            }
            return new Resolution(location, null, LocationResolutionSource.EXPLICIT_MERCHANT_LOCATION);
        }
        boolean hasContext = addressId != null || lat != null || lng != null;
        if (hasContext) {
            LocationContext context = contexts.resolveForDiscovery(lat, lng, addressId);
            MerchantLocationEntity nearest = offersByLocation.entrySet().stream()
                    .filter(entry -> hasAvailableOffer(entry.getValue()))
                    .map(entry -> locationsById.get(entry.getKey()))
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.comparingDouble((MerchantLocationEntity location) -> distanceMeters(context.point(), location.getGeoPoint()))
                            .thenComparing(MerchantLocationEntity::getId))
                    .orElseThrow(CatalogDetailService::notFound);
            return new Resolution(nearest, distanceMeters(context.point(), nearest.getGeoPoint()), context.source());
        }
        MerchantLocationEntity fallback = offersByLocation.entrySet().stream()
                .filter(entry -> hasAvailableOffer(entry.getValue()))
                .map(entry -> locationsById.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(MerchantLocationEntity::getCreatedAt).thenComparing(MerchantLocationEntity::getId))
                .orElseThrow(CatalogDetailService::notFound);
        return new Resolution(fallback, null, LocationResolutionSource.DEFAULT_MERCHANT_LOCATION);
    }

    private List<PublicMediaResponse> media(UUID itemId) {
        return itemMedia.findAllMediaAssetsByCatalogItemId(itemId).stream()
                .filter(asset -> asset.getMediaStatus() == MediaStatus.READY)
                .map(asset -> new PublicMediaResponse(asset.getId(), asset.getMimeType(), mediaUrls.publicUrl(asset)))
                .toList();
    }

    private List<PublicOptionGroupResponse> optionGroups(UUID itemId) {
        List<OptionGroupEntity> groups = optionGroups.findAllByCatalogItemIdOrderBySortOrderAscIdAsc(itemId);
        Map<UUID, List<OptionValueEntity>> valuesByGroup = new HashMap<>();
        if (!groups.isEmpty()) {
            for (OptionValueEntity value : optionValues.findAllByOptionGroupIdInOrderBySortOrderAscIdAsc(
                    groups.stream().map(OptionGroupEntity::getId).toList())) {
                valuesByGroup.computeIfAbsent(value.getOptionGroupId(), unused -> new ArrayList<>()).add(value);
            }
        }
        return groups.stream().map(group -> new PublicOptionGroupResponse(group.getId(), group.getName(),
                valuesByGroup.getOrDefault(group.getId(), List.of()).stream()
                        .map(value -> new PublicOptionValueResponse(value.getId(), value.getValue())).toList())).toList();
    }

    private List<PublicVariantResponse> variantResponses(List<VariantEntity> activeVariants,
            List<LocationVariantEntity> visibleOffers, UUID resolvedLocationId) {
        Map<UUID, LocationVariantEntity> offersAtResolvedLocation = new HashMap<>();
        for (LocationVariantEntity offer : visibleOffers) {
            if (resolvedLocationId.equals(offer.getMerchantLocationId())) {
                offersAtResolvedLocation.put(offer.getVariantId(), offer);
            }
        }
        Map<UUID, List<UUID>> optionValuesByVariant = new HashMap<>();
        if (!activeVariants.isEmpty()) {
            for (var association : variantOptions.findAllByIdVariantIdIn(
                    activeVariants.stream().map(VariantEntity::getId).toList())) {
                optionValuesByVariant.computeIfAbsent(association.getId().getVariantId(), unused -> new ArrayList<>())
                        .add(association.getId().getOptionValueId());
            }
        }
        return activeVariants.stream().map(variant -> {
            LocationVariantEntity offer = offersAtResolvedLocation.get(variant.getId());
            PublicOfferResponse publicOffer = offer == null ? null : new PublicOfferResponse(offer.getId(),
                    offer.isAvailable(), new Money(offer.getPrice(), offer.getCurrencyCode()), offer.getPreparationTimeMinutes());
            List<UUID> optionValueIds = optionValuesByVariant.getOrDefault(variant.getId(), List.of());
            return new PublicVariantResponse(variant.getId(), variant.getVariantName(), variant.isDefaultVariant(),
                    optionValueIds, publicOffer);
        }).toList();
    }

    private static boolean hasAvailableOffer(List<LocationVariantEntity> locationOffers) {
        return locationOffers != null && locationOffers.stream().anyMatch(LocationVariantEntity::isAvailable);
    }

    private static double distanceMeters(Point first, Point second) {
        double lat1 = Math.toRadians(first.getY());
        double lat2 = Math.toRadians(second.getY());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(second.getX() - first.getX());
        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2d) * Math.sin(deltaLng / 2d);
        return EARTH_RADIUS_METERS * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }

    private record Resolution(MerchantLocationEntity location, Double distanceMeters, LocationResolutionSource source) {}
}
