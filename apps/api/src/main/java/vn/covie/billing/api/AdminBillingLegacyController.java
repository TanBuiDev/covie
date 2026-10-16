package vn.covie.billing.api;

import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingLegacyMigrationService;

/**
 * High-risk migration routes require global Admin + separate operator environment approval.
 * Audit and immutable grants prevent invisible changes to existing shops.
 */
@RestController
@RequestMapping("/api/v1/admin/billing/legacy")
public class AdminBillingLegacyController {
    private final BillingLegacyMigrationService migration;
    public AdminBillingLegacyController(BillingLegacyMigrationService migration) {
        this.migration = migration;
    }

    @GetMapping("/preview")
    public ResponseEntity<BillingLegacyMigrationService.Preview> preview(@RequestParam Instant createdBefore) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(migration.preview(createdBefore));
    }

    @PostMapping("/grant")
    public ResponseEntity<BillingLegacyMigrationService.Result> grant(
            @RequestBody BillingLegacyMigrationService.GrantRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(migration.grant(request));
    }
}
