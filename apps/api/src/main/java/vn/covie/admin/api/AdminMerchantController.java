package vn.covie.admin.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.application.AdminMerchantService;
import vn.covie.admin.application.AdminReadService;

@RestController @RequestMapping("/api/v1/admin/merchants")
public final class AdminMerchantController {
    private final AdminMerchantService service; private final AdminReadService reads;
    public AdminMerchantController(AdminMerchantService service,AdminReadService reads){this.service=service;this.reads=reads;}
    @GetMapping public ResponseEntity<AdminPageResponse<AdminMerchantSummaryResponse>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String moderationStatus,@RequestParam(required=false)String q,@RequestParam(required=false)String sort){return noStore(reads.merchants(page,size,status,moderationStatus,q,sort));}
    @GetMapping("/{merchantId}") public ResponseEntity<AdminMerchantSummaryResponse> detail(@PathVariable UUID merchantId){return noStore(reads.merchant(merchantId));}
    @PostMapping("/{merchantId}/suspend") public ResponseEntity<AdminMerchantCommandResult> suspend(@PathVariable UUID merchantId,@Valid @RequestBody AdminReasonRequest request,@RequestHeader("Idempotency-Key") String key){return noStore(service.suspend(merchantId,request,key));}
    @PostMapping("/{merchantId}/restore") public ResponseEntity<AdminMerchantCommandResult> restore(@PathVariable UUID merchantId,@RequestHeader("Idempotency-Key") String key){return noStore(service.restore(merchantId,key));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);}
}
