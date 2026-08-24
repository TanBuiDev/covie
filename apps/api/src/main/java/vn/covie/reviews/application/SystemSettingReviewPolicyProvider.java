package vn.covie.reviews.application;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

@Component
@Primary
public final class SystemSettingReviewPolicyProvider implements ReviewPolicyProvider {
    private final SystemSettingResolver settings;
    public SystemSettingReviewPolicyProvider(SystemSettingResolver settings) { this.settings = settings; }
    @Override public boolean creationEnabled() { return settings.bool("review.enabled", SystemSettingContext.globalOnly()); }
}
