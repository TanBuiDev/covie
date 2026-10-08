package vn.covie.catalog.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.catalog.api.dto.publicview.CatalogItemDetailResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogResponse;
import vn.covie.catalog.application.CatalogDetailService;
import vn.covie.catalog.application.CatalogDiscoveryService;
import vn.covie.catalog.application.PublicMerchantViewService;
import vn.covie.catalog.application.PublicMerchantCatalogService;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryResponse;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/catalog")
public class PublicCatalogController {
    private final CatalogDetailService details;
    private final PublicMerchantViewService merchants;
    private final CatalogDiscoveryService discovery;
    private final PublicMerchantCatalogService merchantCatalog;

    public PublicCatalogController(CatalogDetailService details, PublicMerchantViewService merchants,
            CatalogDiscoveryService discovery, PublicMerchantCatalogService merchantCatalog) {
        this.details = details;
        this.merchants = merchants;
        this.discovery = discovery;
        this.merchantCatalog = merchantCatalog;
    }

    @GetMapping("/items/{itemId}")
    public CatalogItemDetailResponse item(@PathVariable UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID addressId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return details.get(itemId, locationId, addressId, lat, lng);
    }

    @GetMapping("/merchants/{merchantId}")
    public PublicMerchantResponse merchant(@PathVariable UUID merchantId) {
        return merchants.get(merchantId);
    }

    @GetMapping("/merchants/{merchantId}/items")
    public PublicMerchantCatalogResponse merchantItems(@PathVariable UUID merchantId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return merchantCatalog.list(merchantId, limit, cursor);
    }

    @GetMapping("/items")
    public CatalogDiscoveryResponse discover(@RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String listingKind,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) UUID addressId,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Boolean pickup,
            @RequestParam(required = false) Boolean openNow,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Boolean canServe,
            @RequestParam(required = false) String shipping) {
        return discovery.discover(new CatalogDiscoveryQuery(q, enumValue(type, CatalogItemType.class),
                enumValue(listingKind, ListingKind.class), categoryId, lat, lng, addressId, radiusMeters, pickup,
                openNow, minPrice, maxPrice, enumValue(sort, CatalogSort.class), limit, cursor, canServe,
                enumValue(shipping, ShippingMode.class)));
    }

    private static <T extends Enum<T>> T enumValue(String raw, Class<T> enumType) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.CATALOG_FILTER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Catalog discovery filters are invalid.");
        }
    }
}
