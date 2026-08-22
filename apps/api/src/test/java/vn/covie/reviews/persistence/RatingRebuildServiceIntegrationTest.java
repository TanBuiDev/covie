package vn.covie.reviews.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.reviews.application.RatingProjectionService;
import vn.covie.reviews.application.RatingRebuildService;
import vn.covie.support.PostgresIntegrationTest;

class RatingRebuildServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewRepository reviews;
    @Autowired RatingProjectionService projection;
    @Autowired RatingRebuildService rebuild;
    @Autowired CatalogItemRatingStatsRepository catalogStats;
    @Autowired MerchantRatingStatsRepository merchantStats;

    @Test @Transactional
    void rebuildRestoresCorruptedItemAndMerchantAggregatesFromActiveReviews() {
        var fixture=ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var first=fixture.review(fixture.orderItemId(),4,Instant.parse("2026-08-22T00:00:00Z"));
        var second=fixture.review(fixture.anotherOrderItemId(),5,Instant.parse("2026-08-22T00:01:00Z"));
        reviews.save(first); reviews.save(second); projection.add(first); projection.add(second);
        jdbc.update("update catalog_item_rating_stats set review_count=99, rating_sum=99, average_rating=1 where catalog_item_id=?",fixture.catalogItemId());
        jdbc.update("update merchant_rating_stats set review_count=99, rating_sum=99, average_rating=1 where merchant_profile_id=?",fixture.merchantId());

        rebuild.rebuildCatalogItem(fixture.catalogItemId());
        rebuild.rebuildMerchant(fixture.merchantId());

        var item=catalogStats.findById(fixture.catalogItemId()).orElseThrow();
        var merchant=merchantStats.findById(fixture.merchantId()).orElseThrow();
        assertThat(item.reviewCount()).isEqualTo(2); assertThat(item.ratingSum()).isEqualTo(9); assertThat(item.averageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(merchant.reviewCount()).isEqualTo(2); assertThat(merchant.ratingSum()).isEqualTo(9); assertThat(merchant.averageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
    }
}
