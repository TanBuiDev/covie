package vn.covie.moderation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.api.dto.BulkModerationItemResult;
import vn.covie.moderation.api.dto.BulkModerationRequest;
import vn.covie.moderation.api.dto.ModerationActionRequest;
import vn.covie.moderation.api.dto.ModerationCommandResult;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class ModerationBulkItemExecutor {
    private final IdempotencyExecutor idempotency;
    private final ModerationCommandService commands;

    public ModerationBulkItemExecutor(IdempotencyExecutor idempotency, ModerationCommandService commands) {
        this.idempotency = idempotency;
        this.commands = commands;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkModerationItemResult execute(UUID actorId, UUID caseId, BulkModerationRequest request, String batchKey) {
        ModerationActionRequest command = new ModerationActionRequest(request.reasonCode(), request.note());
        String operation = "moderation.bulk." + request.action().name().toLowerCase(java.util.Locale.ROOT) + ":" + caseId;
        return idempotency.execute(actorId, operation, batchKey + ":" + caseId, command,
                200, BulkModerationItemResult.class, () -> {
                    try {
                        ModerationCommandResult result = commands.executeForBulk(caseId, request.action(), command);
                        return BulkModerationItemResult.success(caseId, result.status());
                    } catch (ApiException exception) {
                        if (isExpectedItemFailure(exception.code())) {
                            return BulkModerationItemResult.failure(caseId, exception.code().name());
                        }
                        throw exception;
                    }
                });
    }

    private static boolean isExpectedItemFailure(ErrorCode code) {
        return code == ErrorCode.RESOURCE_NOT_FOUND
                || code == ErrorCode.MODERATION_ACTION_NOT_SUPPORTED
                || code == ErrorCode.MODERATION_CASE_STATE_CONFLICT
                || code == ErrorCode.MODERATION_TARGET_STATE_CONFLICT;
    }
}
