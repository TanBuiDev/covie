package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.AdminBillingGrantService;

@RestController
@RequestMapping("/api/v1/admin/billing")
public class AdminBillingGrantController {
    private final AdminBillingGrantService grants;
    public AdminBillingGrantController(AdminBillingGrantService grants){this.grants=grants;}

    @PostMapping("/merchants/{merchantId}/grants")
    public ResponseEntity<AdminBillingGrantService.Grant> create(
            @PathVariable UUID merchantId,@RequestBody AdminBillingGrantService.Request request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grants.grant(merchantId,request));
    }

    @GetMapping("/merchants/{merchantId}/grants")
    public ResponseEntity<List<AdminBillingGrantService.Grant>> list(@PathVariable UUID merchantId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grants.list(merchantId));
    }

    @PostMapping("/grants/{grantId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID grantId,@RequestBody RevokeRequest request){
        grants.revoke(grantId,request==null?null:request.reason());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record RevokeRequest(String reason){}
}
