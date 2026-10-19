package vn.covie.needs.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.needs.api.dto.NeedResponseCursorPage;
import vn.covie.needs.api.dto.NeedResponseItemResponse;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedResponseItemStatus;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class NeedResponseReadService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private final CurrentActorProvider actors;
    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final NeedResponseItemRepository responseItems;
    private final MerchantProfileRepository merchants;
    private final MerchantLocationRepository locations;
    private final MerchantAccessPolicy merchantAccess;
    private final LocationVariantRepository locationVariants;
    private final VariantRepository variants;
    private final CatalogItemRepository catalogItems;
    private final InventoryBalanceRepository inventory;
    private final AppClock clock;
    private final NeedTimestampCursorCodec cursors;
    private vn.covie.billing.application.BillingEntitlementService billing;

    @org.springframework.beans.factory.annotation.Autowired
    public void setBillingEntitlements(vn.covie.billing.application.BillingEntitlementService billing) {
        this.billing = java.util.Objects.requireNonNull(billing);
    }

    public NeedResponseReadService(CurrentActorProvider actors, NeedRequestRepository needs,
            NeedResponseRepository responses, NeedResponseItemRepository responseItems,
            MerchantProfileRepository merchants, MerchantLocationRepository locations,
            MerchantAccessPolicy merchantAccess,
            LocationVariantRepository locationVariants, VariantRepository variants,
            CatalogItemRepository catalogItems, InventoryBalanceRepository inventory,
            AppClock clock, ObjectMapper mapper) {
        this.actors = actors; this.needs = needs; this.responses = responses; this.responseItems = responseItems;
        this.merchants = merchants; this.locations = locations; this.merchantAccess = merchantAccess;
        this.locationVariants = locationVariants;
        this.variants = variants; this.catalogItems = catalogItems; this.inventory = inventory;
        this.clock = clock; this.cursors = new NeedTimestampCursorCodec(mapper);
    }

    public NeedResponseCursorPage list(UUID needId, Integer requestedLimit, String cursor) {
        UUID buyerId = actors.currentActor().userId();
        NeedRequestEntity need = needs.findByIdAndBuyerUserId(needId, buyerId)
                .orElseThrow(NeedResponseReadService::notFound);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        NeedTimestampCursor after = cursors.decode(cursor);
        List<NeedResponseEntity> page = responses.findPage(needId, NeedResponseStatus.ACTIVE,
                after == null ? null : after.createdAt(), after == null ? null : after.id(), PageRequest.of(0, limit + 1));
        boolean hasMore = page.size() > limit;
        if (hasMore) page = new ArrayList<>(page.subList(0, limit));
        Instant now = clock.now();
        Set<UUID> merchantIds = page.stream().map(NeedResponseEntity::getMerchantProfileId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> allowed = billing == null ? merchantIds : billing.merchantsAllowedToSell(merchantIds);
        ReadRecords records = loadRecords(page, need.getNeedType());
        List<NeedResponseResponse> values = page.stream()
                .map(response -> toDto(need, response, now, allowed.contains(response.getMerchantProfileId()), records)).toList();
        NeedResponseEntity last = page.isEmpty() ? null : page.getLast();
        String next = hasMore && last != null ? cursors.encode(new NeedTimestampCursor(last.getCreatedAt(), last.getId())) : null;
        return new NeedResponseCursorPage(values, hasMore, next);
    }

    public NeedResponseResponse merchantResponse(UUID merchantId, UUID needId, UUID locationId) {
        if (!merchantAccess.allowedLocationIds(merchantId, MerchantCapability.VIEW_NEEDS).contains(locationId)) {
            throw notFound();
        }
        NeedRequestEntity need = needs.findById(needId).orElseThrow(NeedResponseReadService::notFound);
        NeedResponseEntity response = responses
                .findByNeedRequestIdAndMerchantLocationIdAndResponseStatus(
                        needId, locationId, NeedResponseStatus.ACTIVE)
                .filter(value -> value.getMerchantProfileId().equals(merchantId))
                .orElseThrow(NeedResponseReadService::notFound);
        return toDto(need, response, clock.now(), billing == null || billing.evaluate(merchantId).canSell(),
                loadRecords(List.of(response), need.getNeedType()));
    }

    private ReadRecords loadRecords(List<NeedResponseEntity> page, NeedType type) {
        if (page.isEmpty()) return new ReadRecords(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        Set<UUID> merchantIds = page.stream().map(NeedResponseEntity::getMerchantProfileId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> locationIds = page.stream().map(NeedResponseEntity::getMerchantLocationId)
                .collect(java.util.stream.Collectors.toSet());
        List<NeedResponseItemEntity> items = responseItems.findByNeedResponseIdInAndItemStatusOrderByCreatedAtAsc(
                page.stream().map(NeedResponseEntity::getId).toList(), NeedResponseItemStatus.ACTIVE);
        Set<UUID> offerIds = items.stream().map(NeedResponseItemEntity::getLocationVariantId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, LocationVariantEntity> offersById = index(offerIds.isEmpty() ? List.of()
                : locationVariants.findUsableByIdIn(offerIds), LocationVariantEntity::getId);
        Map<UUID, VariantEntity> variantsById = index(variants.findAllById(offersById.values().stream()
                .map(LocationVariantEntity::getVariantId).collect(java.util.stream.Collectors.toSet())), VariantEntity::getId);
        Set<UUID> catalogIds = items.stream().map(NeedResponseItemEntity::getCatalogItemId)
                .collect(java.util.stream.Collectors.toSet());
        variantsById.values().forEach(variant -> catalogIds.add(variant.getCatalogItemId()));
        Set<UUID> trackedIds = offersById.values().stream().filter(offer -> offer.getInventoryMode() == InventoryMode.TRACKED)
                .map(LocationVariantEntity::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, MerchantProfileEntity> merchantsById = merchants.findAllById(merchantIds).stream()
                .filter(merchant -> merchant.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toMap(MerchantProfileEntity::getId, java.util.function.Function.identity()));
        return new ReadRecords(merchantsById,
                index(locations.findAllById(locationIds), MerchantLocationEntity::getId),
                items.stream().collect(java.util.stream.Collectors.groupingBy(NeedResponseItemEntity::getNeedResponseId)),
                offersById, variantsById, index(catalogItems.findAllById(catalogIds), CatalogItemEntity::getId),
                type == NeedType.PRODUCT ? index(inventory.findAllById(trackedIds), InventoryBalanceEntity::getLocationVariantId) : Map.of());
    }

    private static <EntityType> Map<UUID, EntityType> index(List<EntityType> entities,
            java.util.function.Function<EntityType, UUID> identity) {
        return entities.stream().collect(java.util.stream.Collectors.toMap(identity, java.util.function.Function.identity()));
    }

    private record ReadRecords(Map<UUID, MerchantProfileEntity> merchants, Map<UUID, MerchantLocationEntity> locations,
            Map<UUID, List<NeedResponseItemEntity>> items, Map<UUID, LocationVariantEntity> offers,
            Map<UUID, VariantEntity> variants, Map<UUID, CatalogItemEntity> catalogs,
            Map<UUID, InventoryBalanceEntity> inventory) {}

    private NeedResponseResponse toDto(NeedRequestEntity need, NeedResponseEntity response, Instant now,
            boolean billingAllowed, ReadRecords records) {
        var merchant = records.merchants().get(response.getMerchantProfileId());
        MerchantLocationEntity location = records.locations().get(response.getMerchantLocationId());
        Long distance = location == null ? null : haversineMeters(need.getGeoPoint().getY(), need.getGeoPoint().getX(),
                location.getGeoPoint().getY(), location.getGeoPoint().getX());
        boolean needActionable = NeedCommandService.effectiveStatus(need, now).name().equals("ACTIVE")
                && need.getExpiresAt().isAfter(now);
        List<NeedResponseItemResponse> items = records.items().getOrDefault(response.getId(), List.of())
                .stream().map(item -> {
                    var displayCatalog = records.catalogs().get(item.getCatalogItemId());
                    var offer = records.offers().get(item.getLocationVariantId());
                    boolean available = false;
                    NeedResponseItemResponse.MoneyResponse current = null;
                    if (offer != null && offer.isAvailable()) {
                        var variant = records.variants().get(offer.getVariantId());
                        var catalog = variant == null ? null : records.catalogs().get(variant.getCatalogItemId());
                        available = billingAllowed
                                && vn.covie.checkout.application.CheckoutTradingPolicy.merchantVisible(merchant)
                                && vn.covie.checkout.application.CheckoutTradingPolicy.locationActive(location)
                                && variant != null && variant.getDeletedAt() == null
                                && variant.getVariantStatus() == VariantStatus.ACTIVE
                                && catalog != null && catalog.getDeletedAt() == null
                                && catalog.getListingStatus() == ListingStatus.ACTIVE
                                && ("NOT_REQUIRED".equals(catalog.getModerationStatus().name())
                                    || "APPROVED".equals(catalog.getModerationStatus().name()));
                        if (available && need.getNeedType() == NeedType.PRODUCT
                                && offer.getInventoryMode() == InventoryMode.TRACKED) {
                            var balance = records.inventory().get(offer.getId());
                            available = balance != null && balance.availableQuantity() > 0;
                        }
                        current = new NeedResponseItemResponse.MoneyResponse(offer.getPrice(), offer.getCurrencyCode());
                    }
                    return new NeedResponseItemResponse(item.getId(), item.getLocationVariantId(), item.getCatalogItemId(),
                            displayCatalog == null ? null : displayCatalog.getName(), item.getVariantId(),
                            new NeedResponseItemResponse.MoneyResponse(item.getPriceSnapshot(), item.getCurrencyCode()),
                            current, available, needActionable && response.getResponseStatus() == NeedResponseStatus.ACTIVE && available);
                }).toList();
        return new NeedResponseResponse(response.getId(), response.getNeedRequestId(), response.getMerchantProfileId(),
                merchant == null ? null : merchant.getName(), response.getMerchantLocationId(),
                location == null ? null : location.getName(), distance, response.getResponseStatus(), response.getNote(),
                response.getVersion(), response.getCreatedAt(), items);
    }

    private static long haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6_371_008.8d;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round(radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
