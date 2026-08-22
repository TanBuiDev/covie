package vn.covie.reviews.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.reviews.api.dto.PublicReviewResponse;
import vn.covie.reviews.api.dto.PublicReviewerResponse;
import vn.covie.reviews.api.dto.RatingSummary;
import vn.covie.reviews.api.dto.ReviewPageInfo;
import vn.covie.reviews.api.dto.ReviewPageResponse;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.reviews.persistence.CatalogItemRatingStatsRepository;
import vn.covie.reviews.persistence.MerchantRatingStatsRepository;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public class ReviewReadService {
    private final ReviewRepository reviews; private final UserRepository users; private final MediaAssetRepository media;
    private final MediaUrlResolver urls; private final CatalogItemRatingStatsRepository catalogStats; private final MerchantRatingStatsRepository merchantStats;
    private final ReviewCursorCodec cursors;
    public ReviewReadService(ReviewRepository reviews, UserRepository users, MediaAssetRepository media, MediaUrlResolver urls,
            CatalogItemRatingStatsRepository catalogStats, MerchantRatingStatsRepository merchantStats, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.reviews=reviews; this.users=users; this.media=media; this.urls=urls; this.catalogStats=catalogStats; this.merchantStats=merchantStats; this.cursors=new ReviewCursorCodec(mapper);
    }
    @Transactional(readOnly = true)
    public ReviewPageResponse catalog(UUID catalogItemId, Integer requestedLimit, String cursor) {
        return page(catalogItemId, requestedLimit, cursor, true);
    }
    @Transactional(readOnly = true)
    public ReviewPageResponse merchant(UUID merchantId, Integer requestedLimit, String cursor) {
        return page(merchantId, requestedLimit, cursor, false);
    }
    private ReviewPageResponse page(UUID id, Integer requestedLimit, String cursorValue, boolean catalog) {
        int limit = requestedLimit == null ? 20 : Math.max(1, Math.min(50, requestedLimit));
        ReviewCursor cursor = cursors.decode(cursorValue);
        List<ReviewEntity> rows = catalog ? reviews.findActiveCatalogReviews(id, cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.reviewId(), limit + 1)
                : reviews.findActiveMerchantReviews(id, cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.reviewId(), limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);
        String next = hasMore ? cursors.encode(new ReviewCursor(rows.getLast().getCreatedAt(), rows.getLast().getId())) : null;
        RatingSummary rating = catalog ? catalogStats.findById(id).map(s -> new RatingSummary(s.reviewCount(), s.averageRating())).orElse(new RatingSummary(0, BigDecimal.ZERO.setScale(2)))
                : merchantStats.findById(id).map(s -> new RatingSummary(s.reviewCount(), s.averageRating())).orElse(new RatingSummary(0, BigDecimal.ZERO.setScale(2)));
        return new ReviewPageResponse(rows.stream().map(this::publicReview).toList(), new ReviewPageInfo(hasMore, next), rating);
    }
    private PublicReviewResponse publicReview(ReviewEntity review) {
        var user = users.findById(review.getBuyerUserId()).orElse(null);
        String name = user == null ? "Người dùng CoVie" : user.getDisplayName();
        String avatar = user == null || user.getAvatarMediaId() == null ? null : media.findById(user.getAvatarMediaId()).map(asset -> { try { return urls.publicUrl(asset).toString(); } catch (IllegalStateException ignored) { return null; } }).orElse(null);
        return new PublicReviewResponse(review.getId(), review.getRating(), review.getComment(), new PublicReviewerResponse(name, avatar), true, review.getCreatedAt());
    }
}
