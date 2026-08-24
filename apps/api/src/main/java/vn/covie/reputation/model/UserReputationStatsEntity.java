package vn.covie.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_reputation_stats")
public class UserReputationStatsEntity {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "eligible_order_count", nullable = false) private long eligibleOrderCount;
    @Column(name = "completed_order_count", nullable = false) private long completedOrderCount;
    @Column(name = "buyer_no_show_count", nullable = false) private long buyerNoShowCount;
    @Column(name = "buyer_cancel_after_confirm_count", nullable = false) private long buyerCancelAfterConfirmCount;
    @Column(name = "excluded_issue_count", nullable = false) private long excludedIssueCount;
    @Column(name = "completion_rate_basis_points", nullable = false) private int completionRateBasisPoints;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected UserReputationStatsEntity() {}
    public static UserReputationStatsEntity empty(UUID userId, Instant now) { UserReputationStatsEntity stats=new UserReputationStatsEntity(); stats.userId=userId; stats.updatedAt=now; return stats; }
    public void adjust(long eligible, long completed, long noShow, long cancelled, long excluded, Instant now) {
        eligibleOrderCount += eligible; completedOrderCount += completed; buyerNoShowCount += noShow; buyerCancelAfterConfirmCount += cancelled; excludedIssueCount += excluded;
        if (eligibleOrderCount < 0 || completedOrderCount < 0 || buyerNoShowCount < 0 || buyerCancelAfterConfirmCount < 0 || excludedIssueCount < 0) throw new IllegalStateException("Reputation counters cannot be negative.");
        completionRateBasisPoints = basisPoints(completedOrderCount, eligibleOrderCount); updatedAt=now;
    }
    private static int basisPoints(long completed, long eligible) { return eligible == 0 ? 0 : Math.toIntExact((completed * 10_000L) / eligible); }
    public long getEligibleOrderCount(){return eligibleOrderCount;} public long getCompletedOrderCount(){return completedOrderCount;} public int getCompletionRateBasisPoints(){return completionRateBasisPoints;}
}
