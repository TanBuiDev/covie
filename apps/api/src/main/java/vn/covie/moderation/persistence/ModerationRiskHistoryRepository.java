package vn.covie.moderation.persistence;

import java.time.Instant;
import java.util.UUID;

public interface ModerationRiskHistoryRepository {
    boolean merchantHasAdverseAction(UUID merchantId, Instant since);
    boolean userHasAdverseAction(UUID userId, Instant since);
}
