package vn.covie.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_reputation_stats")
public class MerchantReputationStatsEntity {
    @Id @Column(name = "merchant_profile_id") private UUID merchantProfileId;
    @Column(name = "eligible_order_count", nullable = false) private long eligibleOrderCount;
    @Column(name = "completed_order_count", nullable = false) private long completedOrderCount;
    @Column(name = "seller_cancel_after_confirm_count", nullable = false) private long sellerCancelAfterConfirmCount;
    @Column(name = "excluded_issue_count", nullable = false) private long excludedIssueCount;
    @Column(name = "completion_rate_basis_points", nullable = false) private int completionRateBasisPoints;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MerchantReputationStatsEntity() {}
    public static MerchantReputationStatsEntity empty(UUID merchantProfileId, Instant now) { MerchantReputationStatsEntity stats=new MerchantReputationStatsEntity(); stats.merchantProfileId=merchantProfileId; stats.updatedAt=now; return stats; }
    public void adjust(long eligible, long completed, long cancelled, long excluded, Instant now) {
        eligibleOrderCount += eligible; completedOrderCount += completed; sellerCancelAfterConfirmCount += cancelled; excludedIssueCount += excluded;
        if (eligibleOrderCount < 0 || completedOrderCount < 0 || sellerCancelAfterConfirmCount < 0 || excludedIssueCount < 0) throw new IllegalStateException("Reputation counters cannot be negative.");
        completionRateBasisPoints = eligibleOrderCount == 0 ? 0 : Math.toIntExact((completedOrderCount * 10_000L) / eligibleOrderCount); updatedAt=now;
    }
    public long getEligibleOrderCount(){return eligibleOrderCount;} public long getCompletedOrderCount(){return completedOrderCount;} public int getCompletionRateBasisPoints(){return completionRateBasisPoints;}
}
