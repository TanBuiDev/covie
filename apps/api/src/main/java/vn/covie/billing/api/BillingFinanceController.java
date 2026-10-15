package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingFinanceCaseService;

@RestController
public class BillingFinanceController{
    private final BillingFinanceCaseService cases;
    public BillingFinanceController(BillingFinanceCaseService cases){this.cases=cases;}

    @PostMapping("/api/v1/merchants/{merchantId}/billing/invoices/{invoiceId}/finance-cases")
    public ResponseEntity<BillingFinanceCaseService.Case> create(@PathVariable UUID merchantId,
            @PathVariable UUID invoiceId,@RequestBody BillingFinanceCaseService.Request request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(cases.create(merchantId,invoiceId,request));
    }

    @GetMapping("/api/v1/admin/billing/finance-cases")
    public ResponseEntity<List<BillingFinanceCaseService.Case>> list(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="50") int limit){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cases.list(status,limit));
    }

    @PostMapping("/api/v1/admin/billing/finance-cases/{caseId}/resolve")
    public ResponseEntity<BillingFinanceCaseService.Case> resolve(
            @PathVariable UUID caseId,@RequestBody BillingFinanceCaseService.Resolution request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cases.resolve(caseId,request));
    }
}
