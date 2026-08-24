package vn.covie.moderation.application;

import org.springframework.stereotype.Service;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.time.AppClock;

@Service
public class ModerationDecisionService {
    private final SystemSettingResolver settings;
    private final HybridModerationRiskService risk;
    private final AppClock clock;
    public ModerationDecisionService(SystemSettingResolver settings, HybridModerationRiskService risk, AppClock clock) { this.settings = settings; this.risk = risk; this.clock = clock; }
    public ModerationDecision decide(ModerationSubject subject) {
        var context = new SystemSettingContext(subject.merchantId(), subject.categoryId(), subject.locationAreaId());
        ModerationMode mode = ModerationMode.valueOf(settings.enumName("moderation.mode", context));
        return switch (mode) { case AUTO_PUBLISH -> ModerationDecision.PUBLISH_NOW; case PRE_APPROVAL -> ModerationDecision.REQUIRE_MODERATION; case HYBRID -> risk.requiresPreApproval(subject, clock.now()) ? ModerationDecision.REQUIRE_MODERATION : ModerationDecision.PUBLISH_NOW; };
    }
}
