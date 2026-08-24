package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;

public interface ModerationTargetHandler {
    ModerationTargetType supports();
    ModerationTargetSnapshot inspect(UUID targetId);
    ModerationTargetMutationResult apply(UUID targetId,ModerationActionType action,Instant now);
}
