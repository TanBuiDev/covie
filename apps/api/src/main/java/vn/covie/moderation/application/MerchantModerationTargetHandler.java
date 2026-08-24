package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class MerchantModerationTargetHandler implements ModerationTargetHandler {
    private final MerchantProfileRepository merchants; public MerchantModerationTargetHandler(MerchantProfileRepository merchants){this.merchants=merchants;}
    public ModerationTargetType supports(){return ModerationTargetType.MERCHANT;}
    public ModerationTargetSnapshot inspect(UUID id){return snapshot(merchants.findByIdAndDeletedAtIsNull(id).orElseThrow(MerchantModerationTargetHandler::notFound));}
    public ModerationTargetMutationResult apply(UUID id,ModerationActionType action,Instant now){MerchantProfileEntity m=merchants.findActiveForUpdate(id).orElseThrow(MerchantModerationTargetHandler::notFound);var before=snapshot(m);try{switch(action){case HIDE->m.hideModeration(now);case RESTORE->m.restoreModeration(now);default->throw unsupported();}}catch(IllegalStateException e){throw state(e.getMessage());}merchants.saveAndFlush(m);return new ModerationTargetMutationResult(before,snapshot(m));}
    private static ModerationTargetSnapshot snapshot(MerchantProfileEntity m){return new ModerationTargetSnapshot(ModerationTargetType.MERCHANT,m.getId(),m.getMerchantStatus().name(),m.getModerationStatus().name());}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");} private static ApiException unsupported(){return new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,"Moderation action is not supported for Merchant.");} private static ApiException state(String m){return new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT,HttpStatus.CONFLICT,m);}
}
