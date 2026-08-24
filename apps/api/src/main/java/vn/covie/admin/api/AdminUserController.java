package vn.covie.admin.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.application.AdminReadService;
import vn.covie.admin.application.AdminUserService;

@RestController @RequestMapping("/api/v1/admin/users")
public final class AdminUserController {
    private final AdminUserService service; private final AdminReadService reads;
    public AdminUserController(AdminUserService service,AdminReadService reads){this.service=service;this.reads=reads;}
    @GetMapping public ResponseEntity<AdminPageResponse<AdminUserSummaryResponse>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String q,@RequestParam(required=false)String sort){return noStore(reads.users(page,size,status,q,sort));}
    @GetMapping("/{userId}") public ResponseEntity<AdminUserSummaryResponse> detail(@PathVariable UUID userId){return noStore(reads.user(userId));}
    @PostMapping("/{userId}/suspend") public ResponseEntity<AdminUserCommandResult> suspend(@PathVariable UUID userId,@Valid @RequestBody AdminReasonRequest request,@RequestHeader("Idempotency-Key") String key){return noStore(service.suspend(userId,request,key));}
    @PostMapping("/{userId}/restore") public ResponseEntity<AdminUserCommandResult> restore(@PathVariable UUID userId,@RequestHeader("Idempotency-Key") String key){return noStore(service.restore(userId,key));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);}
}
