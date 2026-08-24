package vn.covie.moderation.application;

import java.time.Instant;

public interface HybridModerationRiskService {
    boolean requiresPreApproval(ModerationSubject subject, Instant now);
}
