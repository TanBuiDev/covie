package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.AdminBillingReadService;

@RestController
@RequestMapping("/api/v1/admin/billing")
public class AdminBillingReadController {
    private final AdminBillingReadService data;
    public AdminBillingReadController(AdminBillingReadService data) { this.data=data; }

    @GetMapping("/overview")
    public ResponseEntity<AdminBillingReadService.Overview> overview(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data.overview());
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<AdminBillingReadService.Subscription>> subscriptions(
        @RequestParam(required=false) UUID merchantId,
        @RequestParam(defaultValue="30") int limit,
        @RequestParam(defaultValue="0") int offset){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data.subscriptions(merchantId,limit,offset));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<AdminBillingReadService.Invoice>> invoices(
        @RequestParam(required=false) UUID merchantId,
        @RequestParam(defaultValue="20") int limit,
        @RequestParam(defaultValue="0") int offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(data.invoices(merchantId,limit,offset));
    }

    @GetMapping(value = "/invoices/export", produces = "text/csv")
    public ResponseEntity<String> exportInvoices(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String csv = BillingInvoiceCsv.render(data.invoices(merchantId, limit, offset));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Content-Disposition", "attachment; filename=\"seller-billing-invoices.csv\"")
            .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(csv);
    }
}
