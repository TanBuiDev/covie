package vn.covie.reputation.application;

public interface ReputationPolicyProvider {
    int buyerWarningThresholdBasisPoints();
    long minimumEligibleOrdersForWarning();
    boolean allowMaintenanceRebuild();
}
