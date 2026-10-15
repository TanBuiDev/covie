package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.billing.application.BillingCatalogService;
import vn.covie.billing.application.BillingEntitlementService;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Read contracts and guarded Admin publications; PSP/payment endpoints intentionally absent until provider ADR. */
@RestController
public class BillingController {
    private final BillingCatalogService catalog;
    private final BillingEntitlementService entitlements;
    private final MerchantMembershipRepository memberships;
    private final CurrentActorProvider actors;
    private final AdminAccessPolicy admin;

    public BillingController(BillingCatalogService catalog, BillingEntitlementService entitlements,
                             MerchantMembershipRepository memberships, CurrentActorProvider actors,
                             AdminAccessPolicy admin) {
        this.catalog = catalog;
        this.entitlements = entitlements;
        this.memberships = memberships;
        this.actors = actors;
        this.admin = admin;
    }

    @GetMapping("/api/v1/billing/plans")
    public List<BillingCatalogService.PlanOffer> plans() {
        return catalog.availablePlans();
    }

    @GetMapping("/api/v1/merchants/{merchantId}/billing/summary")
    public ResponseEntity<BillingEntitlementService.Eligibility> summary(@PathVariable UUID merchantId) {
        requireMember(merchantId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(entitlements.evaluate(merchantId));
    }

    @GetMapping("/api/v1/admin/billing/policy")
    public BillingCatalogService.Policy policy() {
        admin.requireAdmin();
        return catalog.currentPolicy();
    }

    @PostMapping("/api/v1/admin/billing/plans/{planCode}/prices")
    public BillingCatalogService.PlanOffer publishPrice(@PathVariable String planCode,
                                      @RequestBody BillingCatalogService.PriceChange request) {
        return catalog.publishPrice(planCode, request);
    }

    @PostMapping("/api/v1/admin/billing/policy")
    public BillingCatalogService.Policy publishPolicy(@RequestBody BillingCatalogService.PolicyChange request) {
        return catalog.publishPolicy(request);
    }

    private void requireMember(UUID merchantId) {
        memberships.findActive(merchantId, actors.currentActor().userId())
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Merchant was not found."));
    }
}
