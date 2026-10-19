package vn.covie.billing.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.billing.application.BillingFinanceAccessPolicy;

/** Global Admin and separate ops approval required for finance role assignment. */
@RestController
@RequestMapping("/api/v1/admin/billing/finance-access")
public class AdminBillingFinanceAccessController {
    private final BillingFinanceAccessPolicy permissions;
    public AdminBillingFinanceAccessController(BillingFinanceAccessPolicy permissions){
        this.permissions=permissions;
    }

    @GetMapping
    public ResponseEntity<List<BillingFinanceAccessPolicy.Assignment>> list(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(permissions.list());
    }
    @PostMapping("/{userId}")
    public ResponseEntity<BillingFinanceAccessPolicy.Assignment> assign(
            @PathVariable UUID userId,@RequestBody RoleAssignment request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(permissions.assign(userId,request==null?null:request.role()));
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID userId){
        permissions.revoke(userId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record RoleAssignment(String role){}
}
