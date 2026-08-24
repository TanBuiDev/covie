package vn.covie.moderation.api;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.moderation.api.dto.*;
import vn.covie.moderation.application.UserReportService;
import vn.covie.shared.idempotency.IdempotencyExecutor;
@RestController @RequestMapping("/api/v1/reports") public class ReportController {
 private final CurrentActorProvider actors; private final IdempotencyExecutor idempotency; private final UserReportService reports;
 public ReportController(CurrentActorProvider actors,IdempotencyExecutor idempotency,UserReportService reports){this.actors=actors;this.idempotency=idempotency;this.reports=reports;}
 @PostMapping public ResponseEntity<ReportCreatedResponse> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateReportRequest request){var actor=actors.currentActor();var response=idempotency.execute(actor.userId(),"report.create:"+request.targetType()+":"+request.targetId(),key,request,201,ReportCreatedResponse.class,()->reports.create(request));return ResponseEntity.status(201).header("Cache-Control","private, no-store").body(response);}
}
