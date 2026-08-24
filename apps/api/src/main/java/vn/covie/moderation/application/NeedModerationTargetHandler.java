package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.needs.persistence.NeedRequestRepository;

@Component
public class NeedModerationTargetHandler implements ModerationTargetHandler {
    private final NeedRequestRepository needs;
    public NeedModerationTargetHandler(NeedRequestRepository needs) { this.needs = needs; }
    @Override public ModerationTargetType supports() { return ModerationTargetType.NEED_REQUEST; }
    @Override @Transactional(readOnly=true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(needs.findById(id).orElseThrow()); }
    @Override @Transactional public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) { var need=needs.findById(id).orElseThrow(); var before=snapshot(need); switch(action) { case APPROVE -> need.approveModeration(now); case REJECT -> need.rejectModeration(now); case HIDE -> need.hide(now); case RESTORE -> need.restoreVisibility(now); default -> throw new IllegalArgumentException("Need action is not supported."); } return new ModerationTargetMutationResult(before,snapshot(need)); }
    private ModerationTargetSnapshot snapshot(vn.covie.needs.model.NeedRequestEntity n) { return new ModerationTargetSnapshot(ModerationTargetType.NEED_REQUEST,n.getId(),n.getRequestStatus().name(),n.getModerationStatus().name()); }
}
