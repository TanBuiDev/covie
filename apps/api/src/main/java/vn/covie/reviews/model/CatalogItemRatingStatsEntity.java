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
@Table(name = "catalog_item_rating_stats")
public class CatalogItemRatingStatsEntity {
    @Id @Column(name = "catalog_item_id") private UUID catalogItemId;
    @Column(name = "review_count", nullable = false) private long reviewCount;
    @Column(name = "rating_sum", nullable = false) private long ratingSum;
    @Column(name = "average_rating", nullable = false) private BigDecimal averageRating;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected CatalogItemRatingStatsEntity() {}
    public static CatalogItemRatingStatsEntity empty(UUID catalogItemId, Instant now) {
        CatalogItemRatingStatsEntity stats = new CatalogItemRatingStatsEntity();
        stats.catalogItemId = catalogItemId; stats.averageRating = BigDecimal.ZERO.setScale(2); stats.updatedAt = now;
        return stats;
    }
    public void add(short rating, Instant now) { reviewCount++; ratingSum += rating; averageRating = average(ratingSum, reviewCount); updatedAt = now; }
    public void remove(short rating, Instant now) { if (reviewCount == 0 || ratingSum < rating) throw new IllegalStateException("Rating projection cannot be negative."); reviewCount--; ratingSum -= rating; averageRating = average(ratingSum, reviewCount); updatedAt = now; }
    private static BigDecimal average(long sum, long count) { return count == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP); }
    public UUID getCatalogItemId() { return catalogItemId; } public long reviewCount() { return reviewCount; } public long ratingSum() { return ratingSum; } public BigDecimal averageRating() { return averageRating; }
}
