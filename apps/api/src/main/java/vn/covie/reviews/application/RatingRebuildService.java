package vn.covie.reviews.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.reviews.model.CatalogItemRatingStatsEntity;
import vn.covie.reviews.model.MerchantRatingStatsEntity;
import vn.covie.reviews.persistence.CatalogItemRatingStatsRepository;
import vn.covie.reviews.persistence.MerchantRatingStatsRepository;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.time.AppClock;

@Service
public class RatingRebuildService {
    private final ReviewRepository reviews;
    private final CatalogItemRatingStatsRepository catalogStats;
    private final MerchantRatingStatsRepository merchantStats;
    private final AppClock clock;
    public RatingRebuildService(ReviewRepository reviews, CatalogItemRatingStatsRepository catalogStats, MerchantRatingStatsRepository merchantStats, AppClock clock) { this.reviews = reviews; this.catalogStats = catalogStats; this.merchantStats = merchantStats; this.clock = clock; }
    @Transactional
    public void rebuildCatalogItem(UUID catalogItemId) {
        Instant now = clock.now();
        catalogStats.ensure(catalogItemId, now);
        Object[] aggregate = reviews.aggregateActiveCatalogReviews(catalogItemId).getFirst();
        catalogStats.findByCatalogItemIdForUpdate(catalogItemId).orElseThrow().replace(number(aggregate[0]), number(aggregate[1]), now);
    }
    @Transactional
    public void rebuildMerchant(UUID merchantProfileId) {
        Instant now = clock.now();
        merchantStats.ensure(merchantProfileId, now);
        Object[] aggregate = reviews.aggregateActiveMerchantReviews(merchantProfileId).getFirst();
        merchantStats.findByMerchantProfileIdForUpdate(merchantProfileId).orElseThrow().replace(number(aggregate[0]), number(aggregate[1]), now);
    }
    private static long number(Object value) { return ((Number) value).longValue(); }
}
