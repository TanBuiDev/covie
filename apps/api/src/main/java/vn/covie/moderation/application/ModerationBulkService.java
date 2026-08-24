package vn.covie.moderation.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.moderation.api.dto.BulkModerationRequest;
import vn.covie.moderation.api.dto.BulkModerationResult;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class ModerationBulkService {
    private final AdminAccessPolicy access;
    private final SystemSettingResolver settings;
    private final ModerationBulkItemExecutor items;

    public ModerationBulkService(AdminAccessPolicy access, SystemSettingResolver settings, ModerationBulkItemExecutor items) {
        this.access = access;
        this.settings = settings;
        this.items = items;
    }

    public BulkModerationResult execute(String idempotencyKey, BulkModerationRequest request) {
        var actor = access.requireAdmin();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, HttpStatus.BAD_REQUEST, "Idempotency-Key is required.");
        }
        if (request == null || (request.action() != ModerationActionType.HIDE && request.action() != ModerationActionType.CLOSE)) {
            throw new ApiException(ErrorCode.MODERATION_BULK_ACTION_NOT_SUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only HIDE and CLOSE are supported for bulk moderation.");
        }
        int maximum = settings.integer("bulk.max_items", SystemSettingContext.globalOnly());
        if (request.caseIds().size() > maximum) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bulk moderation request exceeds the configured maximum.");
        }
        Set<UUID> uniqueCaseIds = new HashSet<>();
        if (request.caseIds().stream().anyMatch(caseId -> caseId == null || !uniqueCaseIds.add(caseId))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bulk moderation case identifiers must be unique.");
        }
        return new BulkModerationResult(request.caseIds().stream()
                .map(caseId -> items.execute(actor.userId(), caseId, request, idempotencyKey))
                .toList());
    }
}
