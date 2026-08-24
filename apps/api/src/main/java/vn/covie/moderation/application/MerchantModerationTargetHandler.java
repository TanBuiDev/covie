package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;

@Component
public class MerchantModerationTargetHandler implements ModerationTargetHandler {
    private final MerchantProfileRepository merchants;
    public MerchantModerationTargetHandler(MerchantProfileRepository merchants) { this.merchants = merchants; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.MERCHANT; }
    @Override @Transactional(readOnly = true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(merchant(id)); }
    @Override @Transactional public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) {
        MerchantProfileEntity merchant = merchant(id);
        ModerationTargetSnapshot before = snapshot(merchant);
        if (action == ModerationActionType.HIDE) merchant.hide(now);
        else if (action == ModerationActionType.RESTORE) merchant.restoreVisibility(now);
        else throw new IllegalArgumentException("Merchant only supports HIDE and RESTORE.");
        return new ModerationTargetMutationResult(before, snapshot(merchant));
    }
    private MerchantProfileEntity merchant(UUID id) { return merchants.findByIdAndDeletedAtIsNull(id).orElseThrow(); }
    private static ModerationTargetSnapshot snapshot(MerchantProfileEntity merchant) {
        return new ModerationTargetSnapshot(ModerationTargetType.MERCHANT, merchant.getId(), merchant.getMerchantStatus().name(), merchant.getModerationStatus().name());
    }
}
