package vn.covie.moderation.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.moderation.api.dto.ModerationActionRequest;
import vn.covie.moderation.api.dto.ModerationCommandResult;
import vn.covie.moderation.model.ModerationActionEntity;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationCaseEntity;
import vn.covie.moderation.model.ModerationCaseStatus;
import vn.covie.moderation.model.ModerationCaseType;
import vn.covie.moderation.persistence.ModerationActionRepository;
import vn.covie.moderation.persistence.ModerationCaseRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class ModerationCommandService {
    private final AdminAccessPolicy access;
    private final ModerationCaseRepository cases;
    private final ModerationActionRepository actions;
    private final ModerationTargetHandlerRegistry handlers;
    private final AuditLogWriter audit;
    private final OutboxEventWriter outbox;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final RequestIdAccessor requestIds;

    public ModerationCommandService(AdminAccessPolicy access, ModerationCaseRepository cases, ModerationActionRepository actions,
            ModerationTargetHandlerRegistry handlers, AuditLogWriter audit, OutboxEventWriter outbox, UuidV7Generator ids,
            AppClock clock, RequestIdAccessor requestIds) {
        this.access = access; this.cases = cases; this.actions = actions; this.handlers = handlers; this.audit = audit;
        this.outbox = outbox; this.ids = ids; this.clock = clock; this.requestIds = requestIds;
    }

    @Transactional
    public ModerationCommandResult executeWithoutIdempotency(UUID caseId, ModerationActionType action, ModerationActionRequest request) {
        CurrentActor actor = access.requireAdmin();
        requireReason(action, request);
        ModerationCaseEntity moderationCase = cases.findByIdForUpdate(caseId).orElseThrow(ModerationCommandService::notFound);
        validate(moderationCase, action);
        Instant now = clock.now();
        var target = moderationCase.target();
        if (action != ModerationActionType.CLOSE) handlers.require(target.type()).apply(target.id(), action, now);
        actions.save(ModerationActionEntity.create(ids.generate(), caseId, action, actor.userId(), request == null ? null : request.reasonCode(),
                request == null ? null : request.note(), JsonNodeFactory.instance.objectNode(), now));
        if (action == ModerationActionType.CLOSE) moderationCase.close(now);
        else if (moderationCase.getCaseStatus() == ModerationCaseStatus.OPEN) moderationCase.resolve(now);
        audit.securityEvent(actor.userId(), "MODERATION_" + action.name(), "MODERATION_CASE", caseId, requestIds.current(),
                Map.of("targetType", target.type().name(), "targetId", target.id().toString()));
        outbox.append(new OutboxEventIntent("MODERATION_ACTION_APPLIED", "MODERATION_CASE", caseId,
                Map.of("action", action.name(), "targetType", target.type().name(), "targetId", target.id().toString())));
        cases.flush();
        return new ModerationCommandResult(caseId, moderationCase.getCaseStatus(), action, moderationCase.getResolvedAt());
    }

    private void validate(ModerationCaseEntity moderationCase, ModerationActionType action) {
        if (moderationCase.isOpen()) {
            if (moderationCase.getCaseType() == ModerationCaseType.PRE_APPROVAL && (action == ModerationActionType.APPROVE || action == ModerationActionType.REJECT || action == ModerationActionType.CLOSE)) return;
            if (moderationCase.getCaseType() != ModerationCaseType.PRE_APPROVAL && (action == ModerationActionType.HIDE || action == ModerationActionType.CLOSE)) return;
            throw unsupported();
        }
        if (moderationCase.getCaseStatus() == ModerationCaseStatus.RESOLVED && action == ModerationActionType.RESTORE) {
            var history = actions.findAllByModerationCaseIdOrderByCreatedAtAscIdAsc(moderationCase.getId());
            if (!history.isEmpty()) {
                ModerationActionType last = history.getLast().getActionType();
                if (last == ModerationActionType.HIDE || last == ModerationActionType.REJECT) return;
            }
        }
        throw stateConflict();
    }
    private static void requireReason(ModerationActionType action, ModerationActionRequest request) {
        if ((action == ModerationActionType.REJECT || action == ModerationActionType.HIDE || action == ModerationActionType.CLOSE)
                && (request == null || request.reasonCode() == null || request.reasonCode().isBlank())) {
            throw new ApiException(ErrorCode.MODERATION_REASON_REQUIRED, HttpStatus.UNPROCESSABLE_ENTITY, "A moderation reason code is required.");
        }
    }
    private static ApiException notFound() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Moderation case was not found."); }
    private static ApiException unsupported() { return new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY, "Action is not supported for this moderation case."); }
    private static ApiException stateConflict() { return new ApiException(ErrorCode.MODERATION_CASE_STATE_CONFLICT, HttpStatus.CONFLICT, "Moderation case is not in a compatible state."); }
}
