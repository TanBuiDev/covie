package vn.covie.admin.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminMerchantCommandResult;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.application.AdminMerchantService;

@RestController @RequestMapping("/api/v1/admin/merchants")
public final class AdminMerchantController {
    private final AdminMerchantService service; public AdminMerchantController(AdminMerchantService service){this.service=service;}
    @PostMapping("/{merchantId}/suspend") public ResponseEntity<AdminMerchantCommandResult> suspend(@PathVariable UUID merchantId,@Valid @RequestBody AdminReasonRequest request,@RequestHeader("Idempotency-Key") String key){return noStore(service.suspend(merchantId,request,key));}
    @PostMapping("/{merchantId}/restore") public ResponseEntity<AdminMerchantCommandResult> restore(@PathVariable UUID merchantId,@RequestHeader("Idempotency-Key") String key){return noStore(service.restore(merchantId,key));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);}
}
