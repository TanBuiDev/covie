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
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.api.dto.AdminUserCommandResult;
import vn.covie.admin.application.AdminUserService;

@RestController @RequestMapping("/api/v1/admin/users")
public final class AdminUserController {
    private final AdminUserService service; public AdminUserController(AdminUserService service){this.service=service;}
    @PostMapping("/{userId}/suspend") public ResponseEntity<AdminUserCommandResult> suspend(@PathVariable UUID userId,@Valid @RequestBody AdminReasonRequest request,@RequestHeader("Idempotency-Key") String key){return noStore(service.suspend(userId,request,key));}
    @PostMapping("/{userId}/restore") public ResponseEntity<AdminUserCommandResult> restore(@PathVariable UUID userId,@RequestHeader("Idempotency-Key") String key){return noStore(service.restore(userId,key));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(body);}
}
