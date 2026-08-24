package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class NeedModerationTargetHandler implements ModerationTargetHandler {
    private final NeedRequestRepository needs; public NeedModerationTargetHandler(NeedRequestRepository needs){this.needs=needs;}
    public ModerationTargetType supports(){return ModerationTargetType.NEED_REQUEST;}
    public ModerationTargetSnapshot inspect(UUID id){return snapshot(needs.findById(id).orElseThrow(NeedModerationTargetHandler::notFound));}
    public ModerationTargetMutationResult apply(UUID id,ModerationActionType action,Instant now){NeedRequestEntity need=needs.lockById(id).orElseThrow(NeedModerationTargetHandler::notFound);var before=snapshot(need);try{switch(action){case APPROVE->need.approveModeration(now);case REJECT->need.rejectModeration(now);case HIDE->need.hideModeration(now);case RESTORE->need.restoreModeration(now);default->throw unsupported();}}catch(IllegalStateException e){throw state(e.getMessage());}needs.saveAndFlush(need);return new ModerationTargetMutationResult(before,snapshot(need));}
    private static ModerationTargetSnapshot snapshot(NeedRequestEntity n){return new ModerationTargetSnapshot(ModerationTargetType.NEED_REQUEST,n.getId(),n.getRequestStatus().name(),n.getModerationStatus().name());}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");} private static ApiException unsupported(){return new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,"Moderation action is not supported for Need.");} private static ApiException state(String m){return new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT,HttpStatus.CONFLICT,m);}
}
