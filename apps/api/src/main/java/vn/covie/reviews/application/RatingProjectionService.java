package vn.covie.reviews.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.reviews.model.ReviewStatus;
import vn.covie.reviews.persistence.CatalogItemRatingStatsRepository;
import vn.covie.reviews.persistence.MerchantRatingStatsRepository;

@Service
public class RatingProjectionService {
    private final CatalogItemRatingStatsRepository catalogStats;
    private final MerchantRatingStatsRepository merchantStats;
    public RatingProjectionService(CatalogItemRatingStatsRepository catalogStats, MerchantRatingStatsRepository merchantStats) { this.catalogStats = catalogStats; this.merchantStats = merchantStats; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(ReviewEntity review) { apply(review, true); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(ReviewEntity review) { apply(review, false); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void onVisibilityTransition(ReviewEntity review, ReviewStatus oldStatus, ReviewStatus newStatus) {
        if (oldStatus != ReviewStatus.ACTIVE && newStatus == ReviewStatus.ACTIVE) add(review);
        else if (oldStatus == ReviewStatus.ACTIVE && newStatus != ReviewStatus.ACTIVE) remove(review);
    }
    private void apply(ReviewEntity review, boolean add) {
        Instant now = review.getUpdatedAt();
        catalogStats.ensure(review.getCatalogItemId(), now);
        var catalog = catalogStats.findByCatalogItemIdForUpdate(review.getCatalogItemId()).orElseThrow();
        merchantStats.ensure(review.getMerchantProfileId(), now);
        var merchant = merchantStats.findByMerchantProfileIdForUpdate(review.getMerchantProfileId()).orElseThrow();
        if (add) { catalog.add(review.getRating(), now); merchant.add(review.getRating(), now); }
        else { catalog.remove(review.getRating(), now); merchant.remove(review.getRating(), now); }
    }
}
