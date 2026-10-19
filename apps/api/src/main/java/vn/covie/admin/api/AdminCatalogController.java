package vn.covie.admin.api;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.AdminCatalogItemDetailResponse;
import vn.covie.admin.api.dto.AdminCatalogCasePageResponse;
import vn.covie.admin.api.dto.AdminCatalogItemSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.application.AdminCatalogReadService;
import vn.covie.admin.application.AdminCatalogCaseOpeningService;
import vn.covie.admin.api.dto.AdminCatalogCaseOpenedResponse;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.catalog.model.*;

@RestController
@RequestMapping("/api/v1/admin/catalog-items")
public class AdminCatalogController {
    private final AdminCatalogReadService reads;
    private final AdminCatalogCaseOpeningService opening;
    private final AdminAccessPolicy access;
    private final IdempotencyExecutor idempotency;
    public AdminCatalogController(AdminCatalogReadService reads, AdminCatalogCaseOpeningService opening,
            AdminAccessPolicy access, IdempotencyExecutor idempotency) {
        this.reads=reads; this.opening=opening; this.access=access; this.idempotency=idempotency;
    }

    @PostMapping("/{itemId}/moderation-cases")
    public ResponseEntity<AdminCatalogCaseOpenedResponse> openCase(@PathVariable UUID itemId,
            @io.swagger.v3.oas.annotations.Parameter(required=true)
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody AdminReasonRequest request) {
        var actor=access.requireAdmin();
        opening.requireEnabled();
        var result=idempotency.execute(actor.userId(),"admin.catalog.open-case:"+itemId,key,request,
                200,AdminCatalogCaseOpenedResponse.class,() -> opening.open(itemId,request));
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<AdminCatalogItemSummaryResponse>> list(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q, @RequestParam(required=false) CatalogItemType type,
            @RequestParam(required=false) ListingKind listingKind, @RequestParam(required=false) ListingStatus listingStatus,
            @RequestParam(required=false) CatalogModerationStatus moderationStatus, @RequestParam(required=false) UUID merchantId,
            @RequestParam(required=false) String sort) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store")
                .body(reads.list(page,size,q,type,listingKind,listingStatus,moderationStatus,merchantId,sort));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<AdminCatalogItemDetailResponse> detail(@PathVariable UUID itemId) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.detail(itemId));
    }

    @GetMapping("/{itemId}/moderation-cases")
    public ResponseEntity<AdminCatalogCasePageResponse> moderationCases(@PathVariable UUID itemId,
            @RequestParam(defaultValue="50") int limit, @RequestParam(required=false) String cursor) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.moderationCases(itemId,limit,cursor));
    }
}
