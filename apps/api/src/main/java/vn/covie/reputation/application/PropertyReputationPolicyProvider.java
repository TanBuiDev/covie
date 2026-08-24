package vn.covie.reputation.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyReputationPolicyProvider implements ReputationPolicyProvider {
    private final int threshold; private final long minimum; private final boolean rebuild;
    public PropertyReputationPolicyProvider(@Value("${covie.reputation.buyer-warning-threshold-basis-points:8000}") int threshold,
            @Value("${covie.reputation.minimum-eligible-orders-for-warning:3}") long minimum,
            @Value("${covie.reputation.allow-maintenance-rebuild:false}") boolean rebuild) {
        this.threshold=threshold; this.minimum=minimum; this.rebuild=rebuild;
    }
    public int buyerWarningThresholdBasisPoints() { return threshold; }
    public long minimumEligibleOrdersForWarning() { return minimum; }
    public boolean allowMaintenanceRebuild() { return rebuild; }
}
