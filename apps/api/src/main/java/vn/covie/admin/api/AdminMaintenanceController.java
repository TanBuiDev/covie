package vn.covie.admin.api;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.application.AdminMaintenanceService;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;
@RestController @RequestMapping("/api/v1/admin/maintenance") public class AdminMaintenanceController{
 private final AdminAccessPolicy access;private final IdempotencyExecutor idempotency;private final AdminMaintenanceService maintenance;
 public AdminMaintenanceController(AdminAccessPolicy access,IdempotencyExecutor idempotency,AdminMaintenanceService maintenance){this.access=access;this.idempotency=idempotency;this.maintenance=maintenance;}
 @PostMapping("/ratings/rebuild") public ResponseEntity<MaintenanceCommandResult> ratings(@RequestHeader("Idempotency-Key")String key,@RequestHeader("X-Admin-Reason")String reason,@Valid @RequestBody RatingRebuildRequest request){return run(key,reason,request,()->maintenance.rebuildRatings(request,reason),"admin.maintenance.ratings");}
 @PostMapping("/reputation/rebuild") public ResponseEntity<MaintenanceCommandResult> reputation(@RequestHeader("Idempotency-Key")String key,@RequestHeader("X-Admin-Reason")String reason,@Valid @RequestBody ReputationRebuildRequest request){return run(key,reason,request,()->maintenance.rebuildReputation(request,reason),"admin.maintenance.reputation");}
 private ResponseEntity<MaintenanceCommandResult> run(String key,String reason,Object request,java.util.function.Supplier<MaintenanceCommandResult> command,String operation){if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("X-Admin-Reason is required and must be at most 500 characters");var actor=access.requireAdmin();var result=idempotency.execute(actor.userId(),operation,key,request,200,MaintenanceCommandResult.class,command);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);}
}
