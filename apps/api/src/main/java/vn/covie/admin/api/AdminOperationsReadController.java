package vn.covie.admin.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminListingSummaryResponse;
import vn.covie.admin.api.dto.AdminNeedSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.api.dto.AdminShopLocationResponse;
import vn.covie.admin.application.AdminOperationsReadService;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsReadController {
    private final AdminOperationsReadService reads;

    public AdminOperationsReadController(AdminOperationsReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/shops")
    public ResponseEntity<AdminPageResponse<AdminShopLocationResponse>> shops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) MerchantStatus merchantStatus,
            @RequestParam(required = false) MerchantLocationStatus locationStatus,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return noStore(reads.shops(page, size, merchantStatus, locationStatus, q, sort));
    }

    @GetMapping("/listings")
    public ResponseEntity<AdminPageResponse<AdminListingSummaryResponse>> listings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) CatalogModerationStatus moderationStatus,
            @RequestParam(required = false) CatalogItemType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return noStore(reads.listings(page, size, status, moderationStatus, type, q, sort));
    }

    @GetMapping("/needs")
    public ResponseEntity<AdminPageResponse<AdminNeedSummaryResponse>> needs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) NeedStatus status,
            @RequestParam(required = false) NeedType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return noStore(reads.needs(page, size, status, type, q, sort));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body);
    }
}
