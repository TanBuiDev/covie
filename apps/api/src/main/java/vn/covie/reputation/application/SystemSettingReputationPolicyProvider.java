package vn.covie.reputation.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

@Component
@Primary
public final class SystemSettingReputationPolicyProvider implements ReputationPolicyProvider {
    private final SystemSettingResolver settings;
    private final boolean allowMaintenanceRebuild;
    public SystemSettingReputationPolicyProvider(SystemSettingResolver settings, @Value("${covie.reputation.allow-maintenance-rebuild:false}") boolean allowMaintenanceRebuild) { this.settings = settings; this.allowMaintenanceRebuild = allowMaintenanceRebuild; }
    @Override public int buyerWarningThresholdBasisPoints() { return settings.integer("reputation.warning_threshold_basis_points", SystemSettingContext.globalOnly()); }
    @Override public long minimumEligibleOrdersForWarning() { return settings.integer("reputation.minimum_eligible_orders_for_warning", SystemSettingContext.globalOnly()); }
    @Override public boolean allowMaintenanceRebuild() { return allowMaintenanceRebuild; }
}
