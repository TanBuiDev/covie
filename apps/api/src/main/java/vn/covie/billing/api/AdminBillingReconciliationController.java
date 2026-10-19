package vn.covie.billing.api;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.billing.application.AdminBillingReconciliationService;
import vn.covie.billing.application.AdminBillingReconciliationService.AdminBillingReconciliationRequest;
import vn.covie.billing.application.AdminBillingReconciliationService.AdminBillingReconciliationResponse;

@RestController
public class AdminBillingReconciliationController {
    private final AdminBillingReconciliationService service;

    public AdminBillingReconciliationController(AdminBillingReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/admin/billing/invoices/{invoiceId}/reconciliation")
    public ResponseEntity<AdminBillingReconciliationResponse> reconcile(@PathVariable UUID invoiceId,
            @Valid @RequestBody AdminBillingReconciliationRequest request,
            @Parameter(required = true) @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reconcile(invoiceId, request, key));
    }
}
