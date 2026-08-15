package vn.covie.catalog.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.catalog.api.dto.publicview.CatalogItemDetailResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantResponse;
import vn.covie.catalog.application.CatalogDetailService;
import vn.covie.catalog.application.PublicMerchantViewService;

@RestController
@RequestMapping("/api/v1/catalog")
public class PublicCatalogController {
    private final CatalogDetailService details;
    private final PublicMerchantViewService merchants;

    public PublicCatalogController(CatalogDetailService details, PublicMerchantViewService merchants) {
        this.details = details;
        this.merchants = merchants;
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
}
