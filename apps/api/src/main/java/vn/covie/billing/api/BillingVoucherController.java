package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingVoucherService;
import vn.covie.billing.application.BillingCheckoutService;

/** Vouchers apply only to Seller Billing. Buyers are never charged here. */
@RestController
public class BillingVoucherController {
    private final BillingVoucherService vouchers;
    private final BillingCheckoutService checkout;
    public BillingVoucherController(BillingVoucherService vouchers,BillingCheckoutService checkout){
        this.vouchers=vouchers;this.checkout=checkout;
    }

    @PostMapping("/api/v1/merchants/{merchantId}/billing/quote")
    public ResponseEntity<BillingCheckoutService.Quote> quote(@PathVariable UUID merchantId,
            @RequestParam String planCode,@RequestParam(required=false) String voucherCode){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(checkout.quote(merchantId,planCode,voucherCode));
    }

    @GetMapping("/api/v1/admin/billing/vouchers")
    public ResponseEntity<List<BillingVoucherService.Voucher>> list(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.list());
    }

    @PostMapping("/api/v1/admin/billing/vouchers")
    public ResponseEntity<BillingVoucherService.Voucher> create(
            @RequestBody BillingVoucherService.CreateRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.create(request));
    }

    @PostMapping("/api/v1/admin/billing/vouchers/{code}/disable")
    public ResponseEntity<Void> disable(@PathVariable String code){
        vouchers.disable(code);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
