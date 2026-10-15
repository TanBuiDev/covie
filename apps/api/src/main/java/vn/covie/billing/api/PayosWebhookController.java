package vn.covie.billing.api;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingWebhookService;

/** Public inbound callback; actual authorization is a valid payOS data signature. */
@RestController
@RequestMapping("/api/v1/billing/webhooks")
public class PayosWebhookController {
    private final BillingWebhookService webhook;
    public PayosWebhookController(BillingWebhookService webhook) { this.webhook = webhook; }

    @PostMapping(value = "/payos", consumes = "application/json")
    public ResponseEntity<Map<String,String>> accept(@RequestBody String payload) {
        String result = webhook.receive(payload);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("status", result));
    }
}
