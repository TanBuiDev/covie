package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingCheckoutService;

/** Seller invoices and payOS links are not buyer order payments. */
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/billing")
public class BillingCheckoutController {
    private final BillingCheckoutService checkout;
    public BillingCheckoutController(BillingCheckoutService checkout) { this.checkout = checkout; }

    @PostMapping("/checkout")
    public ResponseEntity<BillingCheckoutService.CheckoutResult> create(
            @PathVariable UUID merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody BillingCheckoutService.CheckoutRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(checkout.create(merchantId, idempotencyKey, request));
    }

    @GetMapping("/subscription")
    public ResponseEntity<BillingCheckoutService.SubscriptionState> subscription(@PathVariable UUID merchantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(checkout.subscription(merchantId));
    }

    @PostMapping("/cancel-renewal")
    public ResponseEntity<Void> cancelRenewal(@PathVariable UUID merchantId) {
        checkout.cancelRenewal(merchantId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/resume-renewal")
    public ResponseEntity<Void> resumeRenewal(@PathVariable UUID merchantId) {
        checkout.resumeRenewal(merchantId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<BillingCheckoutService.PaymentStatus> paymentStatus(
            @PathVariable UUID merchantId, @PathVariable UUID invoiceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(checkout.paymentStatus(merchantId, invoiceId));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<BillingCheckoutService.InvoiceSummary>> invoices(@PathVariable UUID merchantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.invoices(merchantId));
    }
}
