package vn.covie.reputation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.covie.reputation.api.dto.BuyerReputationSummary;
import vn.covie.reputation.api.dto.MerchantReputationSummary;
import vn.covie.reputation.persistence.MerchantReputationStatsRepository;
import vn.covie.reputation.persistence.UserReputationStatsRepository;

@Service
public class ReputationReadService {
    private final UserReputationStatsRepository users; private final MerchantReputationStatsRepository merchants; private final ReputationPolicyProvider policy;
    public ReputationReadService(UserReputationStatsRepository users, MerchantReputationStatsRepository merchants, ReputationPolicyProvider policy) { this.users=users; this.merchants=merchants; this.policy=policy; }
    public MerchantReputationSummary merchant(UUID merchantProfileId) {
        return merchants.findById(merchantProfileId).map(s -> new MerchantReputationSummary(s.getEligibleOrderCount(), s.getCompletedOrderCount(), rate(s.getEligibleOrderCount(), s.getCompletionRateBasisPoints())))
                .orElseGet(() -> new MerchantReputationSummary(0, 0, null));
    }
    public BuyerReputationSummary buyer(UUID userId) {
        return users.findById(userId).map(s -> { long eligible=s.getEligibleOrderCount(); int value=s.getCompletionRateBasisPoints(); return new BuyerReputationSummary(eligible, s.getCompletedOrderCount(), rate(eligible, value), eligible >= policy.minimumEligibleOrdersForWarning() && value < policy.buyerWarningThresholdBasisPoints()); })
                .orElseGet(() -> new BuyerReputationSummary(0, 0, null, false));
    }
    private static Integer rate(long eligible, int rate) { return eligible == 0 ? null : rate; }
}
