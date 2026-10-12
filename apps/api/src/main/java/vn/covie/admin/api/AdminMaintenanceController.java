package vn.covie.admin.api;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.web.bind.annotation.*;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.application.AdminMaintenanceService;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.idempotency.IdempotencyExecutor;
@RestController @RequestMapping("/api/v1/admin/maintenance") public class AdminMaintenanceController{
 private final AdminAccessPolicy access;private final IdempotencyExecutor idempotency;private final AdminMaintenanceService maintenance;
 public AdminMaintenanceController(AdminAccessPolicy access,IdempotencyExecutor idempotency,AdminMaintenanceService maintenance){this.access=access;this.idempotency=idempotency;this.maintenance=maintenance;}
 @PostMapping("/ratings/rebuild") public ResponseEntity<MaintenanceCommandResult> ratings(@Parameter(required=true) @RequestHeader(value="Idempotency-Key",required=false)String key,@Parameter(required=true) @RequestHeader(value="X-Admin-Reason",required=false)String reason,@Valid @RequestBody RatingRebuildRequest request){return run(key,reason,request,()->maintenance.rebuildRatings(request,reason),"admin.maintenance.ratings");}
 @PostMapping("/reputation/rebuild") public ResponseEntity<MaintenanceCommandResult> reputation(@Parameter(required=true) @RequestHeader(value="Idempotency-Key",required=false)String key,@Parameter(required=true) @RequestHeader(value="X-Admin-Reason",required=false)String reason,@Valid @RequestBody ReputationRebuildRequest request){return run(key,reason,request,()->maintenance.rebuildReputation(request,reason),"admin.maintenance.reputation");}
 private ResponseEntity<MaintenanceCommandResult> run(String key,String reason,Object request,java.util.function.Supplier<MaintenanceCommandResult> command,String operation){var actor=access.requireAdmin();if(reason==null||reason.isBlank()||reason.length()>500)throw new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"X-Admin-Reason is required and must be at most 500 characters");var result=idempotency.execute(actor.userId(),operation,key,request,200,MaintenanceCommandResult.class,command);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);}
}
