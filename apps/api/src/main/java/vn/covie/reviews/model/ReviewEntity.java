package vn.covie.reviews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class ReviewEntity {
    @Id private UUID id;
    @Column(name = "order_item_id", nullable = false) private UUID orderItemId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Column(name = "catalog_item_id", nullable = false) private UUID catalogItemId;
    @Column(name = "variant_id") private UUID variantId;
    @Column(name = "merchant_profile_id", nullable = false) private UUID merchantProfileId;
    @Column(nullable = false) private short rating;
    private String comment;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false) private ReviewStatus reviewStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected ReviewEntity() {}

    public static ReviewEntity active(UUID id, UUID orderItemId, UUID orderId, UUID buyerUserId, UUID catalogItemId,
            UUID variantId, UUID merchantProfileId, short rating, String comment, Instant now) {
        ReviewEntity review = new ReviewEntity();
        review.id = id; review.orderItemId = orderItemId; review.orderId = orderId; review.buyerUserId = buyerUserId;
        review.catalogItemId = catalogItemId; review.variantId = variantId; review.merchantProfileId = merchantProfileId;
        review.rating = rating; review.comment = comment; review.reviewStatus = ReviewStatus.ACTIVE;
        review.createdAt = now; review.updatedAt = now;
        return review;
    }

    public boolean contributesToRating() { return reviewStatus == ReviewStatus.ACTIVE && deletedAt == null; }
    public void hide(Instant now) { reviewStatus = ReviewStatus.HIDDEN; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getOrderId() { return orderId; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public UUID getVariantId() { return variantId; }
    public UUID getMerchantProfileId() { return merchantProfileId; }
    public short getRating() { return rating; }
    public String getComment() { return comment; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
