package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.reviews.persistence.CatalogItemRatingStatsRepository;
import vn.covie.reviews.persistence.MerchantRatingStatsRepository;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.support.PostgresIntegrationTest;

class RatingProjectionServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired RatingProjectionService projections;
    @Autowired TransactionTemplate transactions;
    @Autowired CatalogItemRatingStatsRepository catalogStats;
    @Autowired MerchantRatingStatsRepository merchantStats;
    @Autowired ReviewRepository reviews;
    @Autowired RatingRebuildService rebuilds;
    @Autowired JdbcTemplate jdbc;

    @Test
    void appliesAndRemovesTheSameContributionForCatalogAndMerchant() {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity five = fixture.review(fixture.orderItemId(), (short) 5);
        ReviewEntity three = fixture.review(fixture.anotherOrderItemId(), (short) 3);

        transactions.executeWithoutResult(status -> { projections.add(five); projections.add(three); });
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().reviewCount()).isEqualTo(2);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().ratingSum()).isEqualTo(8);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().averageRating().toPlainString()).isEqualTo("4.00");
        assertThat(merchantStats.findById(fixture.merchantId()).orElseThrow().reviewCount()).isEqualTo(2);

        transactions.executeWithoutResult(status -> projections.remove(five));
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().reviewCount()).isEqualTo(1);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().ratingSum()).isEqualTo(3);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().averageRating().toPlainString()).isEqualTo("3.00");
    }

    @Test
    void rebuildRepairsCorruptedStatsFromActiveReviewSourceRows() {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity five = reviews.saveAndFlush(fixture.review(fixture.orderItemId(), (short) 5));
        ReviewEntity three = reviews.saveAndFlush(fixture.review(fixture.anotherOrderItemId(), (short) 3));
        transactions.executeWithoutResult(status -> { projections.add(five); projections.add(three); });
        jdbc.update("update catalog_item_rating_stats set review_count = 99, rating_sum = 1, average_rating = 0 where catalog_item_id = ?", fixture.catalogItemId());
        jdbc.update("update merchant_rating_stats set review_count = 99, rating_sum = 1, average_rating = 0 where merchant_profile_id = ?", fixture.merchantId());

        rebuilds.rebuildCatalogItem(fixture.catalogItemId());
        rebuilds.rebuildMerchant(fixture.merchantId());

        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().reviewCount()).isEqualTo(2);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().ratingSum()).isEqualTo(8);
        assertThat(merchantStats.findById(fixture.merchantId()).orElseThrow().averageRating().toPlainString()).isEqualTo("4.00");
    }

    @Test
    void concurrentlyProjectsDifferentReviewsForTheSameCatalogAndMerchant() throws Exception {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity five = fixture.review(fixture.orderItemId(), (short) 5);
        ReviewEntity three = fixture.review(fixture.anotherOrderItemId(), (short) 3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> projectAfterBarrier(five, ready, start));
            var second = executor.submit(() -> projectAfterBarrier(three, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().reviewCount()).isEqualTo(2);
        assertThat(catalogStats.findById(fixture.catalogItemId()).orElseThrow().ratingSum()).isEqualTo(8);
        assertThat(merchantStats.findById(fixture.merchantId()).orElseThrow().reviewCount()).isEqualTo(2);
    }

    private void projectAfterBarrier(ReviewEntity review, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        transactions.executeWithoutResult(status -> projections.add(review));
    }

    private record Fixture(UUID buyerId, UUID merchantId, UUID catalogItemId, UUID variantId, UUID orderId, UUID orderItemId, UUID anotherOrderItemId) {
        static Fixture create(JdbcTemplate jdbc) {
            UUID buyer = UUID.randomUUID(), merchant = UUID.randomUUID(), location = UUID.randomUUID();
            UUID category = UUID.randomUUID(), catalogItem = UUID.randomUUID(), variant = UUID.randomUUID();
            UUID order = UUID.randomUUID(), orderItem = UUID.randomUUID(), anotherOrderItem = UUID.randomUUID();
            var now = java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at,version) values (?,'Buyer','ACTIVE',?,?,0)", buyer, now, now);
            jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,'Merchant',?,'ACTIVE','APPROVED',?,?,0)", merchant, "merchant-" + merchant, now, now);
            jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version) values (?,?, 'Main','1 Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,'VND',?,?,0)", location, merchant, now, now);
            jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,'Food',?,'PRODUCT','ACTIVE',0,0)", category, "food-" + category);
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,'Rice',?,'ACTIVE','APPROVED',?,?,0)", catalogItem, merchant, category, "rice-" + catalogItem, now, now);
            jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,'Default',true,'ACTIVE',?,?,0)", variant, catalogItem, now, now);
            jdbc.update("insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,payment_method,subtotal,shipping_fee_type,shipping_fee,known_total,total_amount,currency_code,created_at,updated_at,version) values (?,?,'PRODUCT',?,?,'?','PENDING_CONFIRMATION','MARKETPLACE','COD',10000,'FREE',0,10000,10000,'VND',?,?,0)".replace("'?'", "?"), order, "order-" + order.toString().substring(0, 8), buyer, merchant, location, now, now);
            jdbc.update("insert into order_items(id,order_id,catalog_item_id,variant_id,item_type,quantity,unit_price,line_total,currency_code,item_snapshot,created_at) values (?,?,?,?,'PRODUCT',1,10000,10000,'VND','{}',?)", orderItem, order, catalogItem, variant, now);
            jdbc.update("insert into order_items(id,order_id,catalog_item_id,variant_id,item_type,quantity,unit_price,line_total,currency_code,item_snapshot,created_at) values (?,?,?,?,'PRODUCT',1,10000,10000,'VND','{}',?)", anotherOrderItem, order, catalogItem, variant, now);
            return new Fixture(buyer, merchant, catalogItem, variant, order, orderItem, anotherOrderItem);
        }
        ReviewEntity review(UUID orderItemId, short rating) { return ReviewEntity.active(UUID.randomUUID(), orderItemId, orderId, buyerId, catalogItemId, variantId, merchantId, rating, null, Instant.parse("2026-01-01T00:00:00Z")); }
    }
}
