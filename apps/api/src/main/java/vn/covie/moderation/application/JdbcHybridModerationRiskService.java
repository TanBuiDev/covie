package vn.covie.moderation.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import vn.covie.moderation.persistence.ModerationRiskHistoryRepository;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

@Service
public class JdbcHybridModerationRiskService implements HybridModerationRiskService {
    private final SystemSettingResolver settings;
    private final ModerationRiskHistoryRepository history;

    public JdbcHybridModerationRiskService(SystemSettingResolver settings, ModerationRiskHistoryRepository history) {
        this.settings = settings;
        this.history = history;
    }

    @Override
    public boolean requiresPreApproval(ModerationSubject subject, Instant now) {
        Instant since = now.minus(settings.integer("moderation.hybrid_adverse_lookback_days", SystemSettingContext.globalOnly()), ChronoUnit.DAYS);
        if (subject.merchantId() != null) {
            return history.hasMerchantAdverseAction(subject.merchantId(), since);
        }
        return subject.ownerUserId() != null && history.hasUserAdverseAction(subject.ownerUserId(), since);
    }
}
