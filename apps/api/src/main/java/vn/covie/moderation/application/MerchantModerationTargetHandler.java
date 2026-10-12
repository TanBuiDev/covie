package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

@Component
public class MerchantModerationTargetHandler implements ModerationTargetHandler {
    private final MerchantProfileRepository merchants;
    public MerchantModerationTargetHandler(MerchantProfileRepository merchants) { this.merchants = merchants; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.MERCHANT; }
    @Override @Transactional(readOnly = true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(merchant(id)); }
    @Override @Transactional(noRollbackFor = ApiException.class) public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) {
        MerchantProfileEntity merchant = merchants.findActiveForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant was not found."));
        ModerationTargetSnapshot before = snapshot(merchant);
        try {
            if (action == ModerationActionType.HIDE) merchant.hide(now);
            else if (action == ModerationActionType.RESTORE) merchant.restoreVisibility(now);
            else throw new IllegalArgumentException("Merchant only supports HIDE and RESTORE.");
        } catch (IllegalStateException conflict) {
            throw new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT, HttpStatus.CONFLICT,
                    "Merchant is not in a compatible moderation state.");
        }
        return new ModerationTargetMutationResult(before, snapshot(merchant));
    }
    private MerchantProfileEntity merchant(UUID id) { return merchants.findByIdAndDeletedAtIsNull(id).orElseThrow(); }
    private static ModerationTargetSnapshot snapshot(MerchantProfileEntity merchant) {
        return new ModerationTargetSnapshot(ModerationTargetType.MERCHANT, merchant.getId(), merchant.getMerchantStatus().name(), merchant.getModerationStatus().name());
    }
}
