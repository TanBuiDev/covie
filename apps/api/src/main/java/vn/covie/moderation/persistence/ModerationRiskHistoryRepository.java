package vn.covie.moderation.persistence;

import java.time.Instant;
import java.util.UUID;

public interface ModerationRiskHistoryRepository {
    boolean hasMerchantAdverseAction(UUID merchantId, Instant since);
    boolean hasUserAdverseAction(UUID userId, Instant since);
}
