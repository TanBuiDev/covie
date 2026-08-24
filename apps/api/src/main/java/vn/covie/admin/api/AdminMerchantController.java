package vn.covie.admin.api;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.AdminMerchantCommandResult;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.application.AdminMerchantService;
import vn.covie.admin.application.AdminReadService;
import vn.covie.admin.api.dto.AdminMerchantSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;

@RestController @RequestMapping("/api/v1/admin/merchants")
public class AdminMerchantController {
    private final AdminAccessPolicy access; private final IdempotencyExecutor idempotency; private final AdminMerchantService merchants; private final AdminReadService reads;
    public AdminMerchantController(AdminAccessPolicy access,IdempotencyExecutor idempotency,AdminMerchantService merchants,AdminReadService reads){this.access=access;this.idempotency=idempotency;this.merchants=merchants;this.reads=reads;}
    @GetMapping public ResponseEntity<AdminPageResponse<AdminMerchantSummaryResponse>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)vn.covie.merchants.model.MerchantStatus status,@RequestParam(required=false)vn.covie.merchants.model.MerchantModerationStatus moderationStatus,@RequestParam(required=false)String q,@RequestParam(required=false)String sort){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.merchants(page,size,status,moderationStatus,q,sort));}
    @GetMapping("/{merchantId}") public ResponseEntity<AdminMerchantSummaryResponse> detail(@PathVariable UUID merchantId){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.merchant(merchantId));}
    @PostMapping("/{merchantId}/suspend") public ResponseEntity<AdminMerchantCommandResult> suspend(@PathVariable UUID merchantId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AdminReasonRequest request){var actor=access.requireAdmin();var result=idempotency.execute(actor.userId(),"admin.merchant.suspend:"+merchantId,key,request,200,AdminMerchantCommandResult.class,()->merchants.suspend(merchantId,request));return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);}
    @PostMapping("/{merchantId}/restore") public ResponseEntity<AdminMerchantCommandResult> restore(@PathVariable UUID merchantId,@RequestHeader("Idempotency-Key") String key){var actor=access.requireAdmin();var result=idempotency.execute(actor.userId(),"admin.merchant.restore:"+merchantId,key,Map.of(),200,AdminMerchantCommandResult.class,()->merchants.restore(merchantId));return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);}
}
