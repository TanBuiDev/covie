package vn.covie.reviews.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.support.PostgresIntegrationTest;

class ReviewRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ReviewRepository reviews;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void orderItemCanOnlyHaveOneReview() {
        Fixture fixture = Fixture.create(jdbc);
        reviews.saveAndFlush(fixture.review(fixture.orderItemId(), 4, Instant.parse("2026-01-01T00:00:00Z")));

        assertThatThrownBy(() -> reviews.saveAndFlush(
                fixture.review(fixture.orderItemId(), 5, Instant.parse("2026-01-01T00:01:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsActiveCatalogReviewsNewestFirst() {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity older = fixture.review(fixture.orderItemId(), 4, Instant.parse("2026-01-01T00:00:00Z"));
        ReviewEntity newer = fixture.review(fixture.anotherOrderItemId(), 5, Instant.parse("2026-01-02T00:00:00Z"));
        reviews.saveAndFlush(older);
        reviews.saveAndFlush(newer);

        assertThat(reviews.findActiveCatalogReviews(fixture.catalogItemId(), null, null, 10))
                .extracting(ReviewEntity::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void hiddenReviewIsNotReturnedByPublicQuery() {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity review = fixture.review(fixture.orderItemId(), 4, Instant.parse("2026-01-01T00:00:00Z"));
        review.hide(Instant.parse("2026-01-02T00:00:00Z"));
        reviews.saveAndFlush(review);

        assertThat(reviews.findActiveCatalogReviews(fixture.catalogItemId(), null, null, 10)).isEmpty();
    }

    @Test
    void findsActiveMerchantReviewsNewestFirst() {
        Fixture fixture = Fixture.create(jdbc);
        ReviewEntity older = fixture.review(fixture.orderItemId(), 4, Instant.parse("2026-01-01T00:00:00Z"));
        ReviewEntity newer = fixture.review(fixture.anotherOrderItemId(), 5, Instant.parse("2026-01-02T00:00:00Z"));
        reviews.saveAndFlush(older);
        reviews.saveAndFlush(newer);

        assertThat(reviews.findActiveMerchantReviews(fixture.merchantId(), null, null, 10))
                .extracting(ReviewEntity::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    record Fixture(UUID buyerId, UUID merchantId, UUID catalogItemId, UUID variantId,
                           UUID orderId, UUID orderItemId, UUID anotherOrderItemId) {
        static Fixture create(JdbcTemplate jdbc) {
            UUID buyerId = UUID.randomUUID();
            UUID merchantId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            UUID categoryId = UUID.randomUUID();
            UUID catalogItemId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID orderItemId = UUID.randomUUID();
            UUID anotherOrderItemId = UUID.randomUUID();
            java.sql.Timestamp now = java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));

            jdbc.update("insert into users(id, display_name, account_status, created_at, updated_at, version) values (?, 'Buyer', 'ACTIVE', ?, ?, 0)", buyerId, now, now);
            jdbc.update("insert into merchant_profiles(id, name, slug, merchant_status, moderation_status, created_at, updated_at, version) values (?, 'Merchant', ?, 'ACTIVE', 'APPROVED', ?, ?, 0)", merchantId, "merchant-" + merchantId, now, now);
            jdbc.update("""
                    insert into merchant_locations(id, merchant_profile_id, name, address_text, geo_point, pin_source,
                    timezone, location_status, pickup_enabled, seller_delivery_enabled, service_enabled, currency_code,
                    created_at, updated_at, version)
                    values (?, ?, 'Main', '1 Street', ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'), 'GPS',
                    'Asia/Ho_Chi_Minh', 'ACTIVE', true, false, false, 'VND', ?, ?, 0)
                    """, locationId, merchantId, now, now);
            jdbc.update("insert into categories(id, name, slug, catalog_type, status, sort_order, version) values (?, 'Food', ?, 'PRODUCT', 'ACTIVE', 0, 0)", categoryId, "food-" + categoryId);
            jdbc.update("""
                    insert into catalog_items(id, merchant_profile_id, item_type, listing_kind, primary_category_id, name,
                    slug, listing_status, moderation_status, created_at, updated_at, version)
                    values (?, ?, 'PRODUCT', 'STANDARD', ?, 'Rice', ?, 'ACTIVE', 'APPROVED', ?, ?, 0)
                    """, catalogItemId, merchantId, categoryId, "rice-" + catalogItemId, now, now);
            jdbc.update("insert into variants(id, catalog_item_id, variant_name, is_default, variant_status, created_at, updated_at, version) values (?, ?, 'Default', true, 'ACTIVE', ?, ?, 0)", variantId, catalogItemId, now, now);
            jdbc.update("""
                    insert into orders(id, order_code, order_type, buyer_user_id, merchant_profile_id, merchant_location_id,
                    status, source_type, payment_method, subtotal, shipping_fee_type, shipping_fee, known_total,
                    total_amount, currency_code, created_at, updated_at, version)
                    values (?, ?, 'PRODUCT', ?, ?, ?, 'PENDING_CONFIRMATION', 'MARKETPLACE', 'COD', 10000, 'FREE', 0,
                    10000, 10000, 'VND', ?, ?, 0)
                    """, orderId, "order-" + orderId.toString().substring(0, 8), buyerId, merchantId, locationId, now, now);
            for (UUID itemId : new UUID[] {orderItemId, anotherOrderItemId}) {
                jdbc.update("""
                        insert into order_items(id, order_id, catalog_item_id, variant_id, item_type, quantity, unit_price,
                        line_total, currency_code, item_snapshot, created_at)
                        values (?, ?, ?, ?, 'PRODUCT', 1, 10000, 10000, 'VND', '{}', ?)
                        """, itemId, orderId, catalogItemId, variantId, now);
            }
            return new Fixture(buyerId, merchantId, catalogItemId, variantId, orderId, orderItemId, anotherOrderItemId);
        }

        ReviewEntity review(UUID orderItemId, int rating, Instant createdAt) {
            return ReviewEntity.active(UUID.randomUUID(), orderItemId, orderId, buyerId, catalogItemId, variantId,
                    merchantId, (short) rating, "Helpful", createdAt);
        }
    }
}
