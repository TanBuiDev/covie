package vn.covie.moderation.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import vn.covie.moderation.persistence.ModerationRiskHistoryRepository;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

@Service
public final class HybridModerationRiskService {
    private final SystemSettingResolver settings;
    private final ModerationRiskHistoryRepository history;

    public HybridModerationRiskService(SystemSettingResolver settings, ModerationRiskHistoryRepository history) {
        this.settings = settings;
        this.history = history;
    }

    public boolean requiresPreApproval(ModerationSubject subject, Instant now) {
        int lookbackDays = settings.integer("moderation.hybrid_adverse_lookback_days", SystemSettingContext.globalOnly());
        Instant since = now.minus(lookbackDays, ChronoUnit.DAYS);
        if (subject.merchantId() != null && history.merchantHasAdverseAction(subject.merchantId(), since)) return true;
        return subject.ownerUserId() != null && history.userHasAdverseAction(subject.ownerUserId(), since);
    }
}
