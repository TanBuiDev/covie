package vn.covie.reviews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_rating_stats")
public class MerchantRatingStatsEntity {
    @Id @Column(name = "merchant_profile_id") private UUID merchantProfileId;
    @Column(name = "review_count", nullable = false) private long reviewCount;
    @Column(name = "rating_sum", nullable = false) private long ratingSum;
    @Column(name = "average_rating", nullable = false) private BigDecimal averageRating;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MerchantRatingStatsEntity() {}
    public static MerchantRatingStatsEntity empty(UUID merchantProfileId, Instant now) { MerchantRatingStatsEntity stats = new MerchantRatingStatsEntity(); stats.merchantProfileId = merchantProfileId; stats.averageRating = BigDecimal.ZERO.setScale(2); stats.updatedAt = now; return stats; }
    public void add(short rating, Instant now) { reviewCount++; ratingSum += rating; averageRating = average(ratingSum, reviewCount); updatedAt = now; }
    public void remove(short rating, Instant now) { if (reviewCount == 0 || ratingSum < rating) throw new IllegalStateException("Rating projection cannot be negative."); reviewCount--; ratingSum -= rating; averageRating = average(ratingSum, reviewCount); updatedAt = now; }
    public void replace(long reviewCount, long ratingSum, Instant now) { if (reviewCount < 0 || ratingSum < 0) throw new IllegalArgumentException("Rating projection cannot be negative."); this.reviewCount = reviewCount; this.ratingSum = ratingSum; this.averageRating = average(ratingSum, reviewCount); this.updatedAt = now; }
    private static BigDecimal average(long sum, long count) { return count == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP); }
    public UUID getMerchantProfileId() { return merchantProfileId; } public long reviewCount() { return reviewCount; } public long ratingSum() { return ratingSum; } public BigDecimal averageRating() { return averageRating; }
}
