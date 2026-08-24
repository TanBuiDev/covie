package vn.covie.moderation.api;

import jakarta.validation.Valid;
import java.util.UUID;
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
import vn.covie.moderation.api.dto.ModerationCommandResult;
import vn.covie.moderation.application.ModerationCommandService;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.shared.idempotency.IdempotencyExecutor;

@RestController
@RequestMapping("/api/v1/admin/moderation-cases")
public class AdminModerationController {
    private final AdminAccessPolicy access;
    private final IdempotencyExecutor idempotency;
    private final ModerationCommandService commands;
    public AdminModerationController(AdminAccessPolicy access, IdempotencyExecutor idempotency, ModerationCommandService commands) {
        this.access = access; this.idempotency = idempotency; this.commands = commands;
    }
    @PostMapping("/{caseId}/approve") public ResponseEntity<ModerationCommandResult> approve(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody(required = false) ModerationActionRequest request) { return command(caseId, ModerationActionType.APPROVE, key, request); }
    @PostMapping("/{caseId}/reject") public ResponseEntity<ModerationCommandResult> reject(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.REJECT, key, request); }
    @PostMapping("/{caseId}/hide-content") public ResponseEntity<ModerationCommandResult> hide(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.HIDE, key, request); }
    @PostMapping("/{caseId}/restore-content") public ResponseEntity<ModerationCommandResult> restore(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody(required = false) ModerationActionRequest request) { return command(caseId, ModerationActionType.RESTORE, key, request); }
    @PostMapping("/{caseId}/close") public ResponseEntity<ModerationCommandResult> close(@PathVariable UUID caseId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ModerationActionRequest request) { return command(caseId, ModerationActionType.CLOSE, key, request); }
    private ResponseEntity<ModerationCommandResult> command(UUID caseId, ModerationActionType action, String key, ModerationActionRequest request) {
        var actor = access.requireAdmin();
        ModerationCommandResult result = idempotency.execute(actor.userId(), "moderation." + action.name().toLowerCase() + ":" + caseId,
                key, request == null ? java.util.Map.of() : request, HttpStatus.OK.value(), ModerationCommandResult.class,
                () -> commands.executeWithoutIdempotency(caseId, action, request));
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(result);
    }
}
