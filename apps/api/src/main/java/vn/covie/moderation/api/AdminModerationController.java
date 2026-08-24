package vn.covie.moderation.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.moderation.api.dto.ModerationActionRequest;
import vn.covie.moderation.api.dto.BulkModerationRequest;
import vn.covie.moderation.api.dto.BulkModerationResult;
import vn.covie.moderation.api.dto.ModerationCommandResult;
import vn.covie.moderation.application.ModerationBulkService;
import vn.covie.moderation.application.ModerationCommandService;
import vn.covie.moderation.application.ModerationCaseService;
import vn.covie.moderation.api.dto.ModerationCaseSummaryResponse;
import vn.covie.moderation.api.dto.ModerationCaseDetailResponse;
import vn.covie.moderation.model.ModerationCaseStatus;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.shared.idempotency.IdempotencyExecutor;

@RestController
@RequestMapping("/api/v1/admin/moderation-cases")
public class AdminModerationController {
    private final AdminAccessPolicy access;
    private final IdempotencyExecutor idempotency;
    private final ModerationCommandService commands;
    private final ModerationBulkService bulk;
    private final ModerationCaseService cases;
    public AdminModerationController(AdminAccessPolicy access, IdempotencyExecutor idempotency, ModerationCommandService commands, ModerationBulkService bulk, ModerationCaseService cases) {
        this.access = access; this.idempotency = idempotency; this.commands = commands; this.bulk = bulk; this.cases=cases;
    }
    @org.springframework.web.bind.annotation.GetMapping public ResponseEntity<Page<ModerationCaseSummaryResponse>> page(@org.springframework.web.bind.annotation.RequestParam(defaultValue="0")int page,@org.springframework.web.bind.annotation.RequestParam(defaultValue="50")int size,@org.springframework.web.bind.annotation.RequestParam(required=false)ModerationCaseStatus status,@org.springframework.web.bind.annotation.RequestParam(defaultValue="OPENED_DESC")String sort){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(cases.page(page,size,status,sort));}
    @org.springframework.web.bind.annotation.GetMapping("/{caseId}") public ResponseEntity<ModerationCaseDetailResponse> detail(@PathVariable UUID caseId){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(cases.detail(caseId));}
    @PostMapping("/{caseId}/approve") public ResponseEntity<ModerationCommandResult> approve(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody(required = false) ModerationActionRequest request) { return command(caseId, ModerationActionType.APPROVE, key, request); }
    @PostMapping("/{caseId}/reject") public ResponseEntity<ModerationCommandResult> reject(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.REJECT, key, request); }
    @PostMapping("/{caseId}/hide-content") public ResponseEntity<ModerationCommandResult> hide(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.HIDE, key, request); }
    @PostMapping("/{caseId}/restore-content") public ResponseEntity<ModerationCommandResult> restore(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody(required = false) ModerationActionRequest request) { return command(caseId, ModerationActionType.RESTORE, key, request); }
    @PostMapping("/{caseId}/close") public ResponseEntity<ModerationCommandResult> close(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.CLOSE, key, request); }
    @PostMapping("/bulk-actions") public ResponseEntity<BulkModerationResult> bulk(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BulkModerationRequest request) { return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(bulk.execute(key, request)); }
    private ResponseEntity<ModerationCommandResult> command(UUID caseId, ModerationActionType action, String key, ModerationActionRequest request) {
        var actor = access.requireAdmin();
        ModerationCommandResult result = idempotency.execute(actor.userId(), "moderation." + action.name().toLowerCase() + ":" + caseId,
                key, request == null ? java.util.Map.of() : request, HttpStatus.OK.value(), ModerationCommandResult.class,
                () -> commands.executeWithoutIdempotency(caseId, action, request));
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(result);
    }
}
