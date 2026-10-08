package vn.covie.needs.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.model.VariantStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.needs.api.dto.NeedResponseCursorPage;
import vn.covie.needs.api.dto.NeedResponseItemResponse;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
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
        List<NeedResponseResponse> values = page.stream().map(response -> toDto(need, response, now)).toList();
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
        return toDto(need, response, clock.now());
    }

    private NeedResponseResponse toDto(NeedRequestEntity need, NeedResponseEntity response, Instant now) {
        var merchant = merchants.findByIdAndDeletedAtIsNull(response.getMerchantProfileId()).orElse(null);
        MerchantLocationEntity location = locations.findById(response.getMerchantLocationId()).orElse(null);
        Long distance = location == null ? null : haversineMeters(need.getGeoPoint().getY(), need.getGeoPoint().getX(),
                location.getGeoPoint().getY(), location.getGeoPoint().getX());
        boolean needActionable = NeedCommandService.effectiveStatus(need, now).name().equals("ACTIVE")
                && need.getExpiresAt().isAfter(now);
        List<NeedResponseItemResponse> items = responseItems
                .findByNeedResponseIdAndItemStatusOrderByCreatedAtAsc(response.getId(), NeedResponseItemStatus.ACTIVE)
                .stream().map(item -> {
                    var displayCatalog = catalogItems.findById(item.getCatalogItemId()).orElse(null);
                    var offer = locationVariants.findUsableById(item.getLocationVariantId()).orElse(null);
                    boolean available = false;
                    NeedResponseItemResponse.MoneyResponse current = null;
                    if (offer != null && offer.isAvailable()) {
                        var variant = variants.findById(offer.getVariantId()).orElse(null);
                        var catalog = variant == null ? null : catalogItems.findById(variant.getCatalogItemId()).orElse(null);
                        available = variant != null && variant.getDeletedAt() == null
                                && variant.getVariantStatus() == VariantStatus.ACTIVE
                                && catalog != null && catalog.getDeletedAt() == null
                                && catalog.getListingStatus() == ListingStatus.ACTIVE
                                && ("NOT_REQUIRED".equals(catalog.getModerationStatus().name())
                                    || "APPROVED".equals(catalog.getModerationStatus().name()));
                        if (available && need.getNeedType() == NeedType.PRODUCT
                                && offer.getInventoryMode() == InventoryMode.TRACKED) {
                            available = inventory.findById(offer.getId()).map(v -> v.availableQuantity() > 0).orElse(false);
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
