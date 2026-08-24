package vn.covie.admin.api;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.api.dto.AdminUserCommandResult;
import vn.covie.admin.application.AdminUserService;
import vn.covie.admin.application.AdminReadService;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.api.dto.AdminUserSummaryResponse;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminAccessPolicy access; private final IdempotencyExecutor idempotency; private final AdminUserService users; private final AdminReadService reads;
    public AdminUserController(AdminAccessPolicy access, IdempotencyExecutor idempotency, AdminUserService users, AdminReadService reads) { this.access=access;this.idempotency=idempotency;this.users=users;this.reads=reads; }
    @org.springframework.web.bind.annotation.GetMapping public ResponseEntity<AdminPageResponse<AdminUserSummaryResponse>> list(@org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,@org.springframework.web.bind.annotation.RequestParam(defaultValue="50") int size,@org.springframework.web.bind.annotation.RequestParam(required=false) vn.covie.users.model.AccountStatus status,@org.springframework.web.bind.annotation.RequestParam(required=false) String q,@org.springframework.web.bind.annotation.RequestParam(required=false) String sort){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.users(page,size,status,q,sort));}
    @org.springframework.web.bind.annotation.GetMapping("/{userId}") public ResponseEntity<AdminUserSummaryResponse> detail(@PathVariable UUID userId){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reads.user(userId));}
    @PostMapping("/{userId}/suspend") public ResponseEntity<AdminUserCommandResult> suspend(@PathVariable UUID userId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AdminReasonRequest request) { var actor=access.requireAdmin(); var result=idempotency.execute(actor.userId(),"admin.user.suspend:"+userId,key,request,200,AdminUserCommandResult.class,()->users.suspend(userId,request)); return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result); }
    @PostMapping("/{userId}/restore") public ResponseEntity<AdminUserCommandResult> restore(@PathVariable UUID userId, @RequestHeader("Idempotency-Key") String key) { var actor=access.requireAdmin(); var result=idempotency.execute(actor.userId(),"admin.user.restore:"+userId,key,Map.of(),200,AdminUserCommandResult.class,()->users.restore(userId)); return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result); }
}
