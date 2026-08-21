package vn.covie.checkout.concurrency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutCommitHook;
import vn.covie.checkout.application.CheckoutCommitService;
import vn.covie.checkout.application.CheckoutComparableItem;
import vn.covie.checkout.application.CheckoutComparableSnapshot;
import vn.covie.checkout.application.CheckoutComparisonService;
import vn.covie.checkout.application.CheckoutCandidate;
import vn.covie.checkout.application.CheckoutCandidateItem;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.checkout.application.CheckoutRevalidation;
import vn.covie.checkout.application.CheckoutSessionService;
import vn.covie.checkout.application.FulfillmentSnapshots;
import vn.covie.checkout.application.RevalidatedCheckout;
import vn.covie.checkout.application.RevalidatedCheckoutItem;
import vn.covie.checkout.model.CheckoutSessionEntity;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CheckoutRollbackInvariantTest extends PostgresIntegrationTest {
    @Autowired CheckoutCommitService commits;
    @Autowired ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired protected InventoryBalanceRepository balances;
    @MockitoBean CheckoutSessionService sessions;
    @MockitoBean CheckoutPreviewService previews;
    @MockitoBean CheckoutComparisonService comparison;
    @MockitoBean CheckoutCommitHook hook;

    @Test
    void failureAfterOrderCreationRollsBackOrderReservationMovementAndOutbox() throws Exception {
        Fixture fixture = fixture();
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(fixture.offerId, 1)), FulfillmentMethod.PICKUP, null, PaymentMethod.COD);
        CheckoutComparableSnapshot snapshot = new CheckoutComparableSnapshot(CheckoutSourceType.DIRECT, fixture.locationId, null,
                FulfillmentMethod.PICKUP, null, null, 10_000, 0L, 10_000L, 10_000L, null, "VND",
                List.of(new CheckoutComparableItem(fixture.offerId, 1, 10_000, true)));
        CheckoutSessionEntity session = CheckoutSessionEntity.create(UUID.randomUUID(), "hash", fixture.buyerId,
                CheckoutSourceType.DIRECT, mapper.createObjectNode(), null, mapper.valueToTree(request),
                mapper.valueToTree(snapshot), Instant.now().plusSeconds(300), Instant.now());
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, fixture.buyerId, fixture.merchantId,
                fixture.locationId, CatalogItemType.PRODUCT, List.of(new CheckoutCandidateItem(fixture.offerId, fixture.variantId,
                fixture.itemId, 1)), null, mapper.createObjectNode());
        RevalidatedCheckout checkout = new RevalidatedCheckout(candidate, FulfillmentMethod.PICKUP, PaymentMethod.COD,
                List.of(new RevalidatedCheckoutItem(candidate.items().getFirst(), 10_000, mapper.createObjectNode())),
                10_000, "VND", null, new FulfillmentSnapshots(null, null, mapper.createObjectNode(), null,
                mapper.createObjectNode(), mapper.createObjectNode(), null));
        CheckoutRevalidation revalidated = new CheckoutRevalidation(checkout, snapshot, 0L, 10_000L, 10_000L, null, Set.of(fixture.offerId));
        when(sessions.lock("token", fixture.buyerId)).thenReturn(session);
        when(previews.revalidate(eq(fixture.buyerId), any())).thenReturn(revalidated);
        when(previews.revalidate(eq(fixture.buyerId), any(), anyMap())).thenReturn(revalidated);
        when(comparison.compare(any(), any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("test rollback")).when(hook).afterOrderCreated(any());
        int orderItemsBefore = count("order_items");
        int fulfillmentsBefore = count("product_fulfillments");
        int orderEventsBefore = count("order_events");
        int outboxBefore = count("outbox_events");

        assertThatThrownBy(() -> commits.commit(fixture.buyerId, "token")).isInstanceOf(IllegalStateException.class);

        assertCount("orders", "buyer_user_id", fixture.buyerId, 0);
        assertThat(count("order_items")).isEqualTo(orderItemsBefore);
        assertThat(count("product_fulfillments")).isEqualTo(fulfillmentsBefore);
        assertThat(count("order_events")).isEqualTo(orderEventsBefore);
        assertThat(count("outbox_events")).isEqualTo(outboxBefore);
        assertCount("inventory_reservations", "location_variant_id", fixture.offerId, 0);
        assertCount("inventory_movements", "location_variant_id", fixture.offerId, 0);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id = ?", Long.class, fixture.offerId)).isZero();
        assertThat(session.getConsumedAt()).isNull();
    }

    private void assertCount(String table, String column, UUID id, int expected) {
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, id)).isEqualTo(expected);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    protected Fixture fixture() {
        Instant now = Instant.now(); Timestamp timestamp = Timestamp.from(now); UUID buyer = UUID.randomUUID(), merchant = UUID.randomUUID(), location = UUID.randomUUID();
        UUID category = UUID.randomUUID(), item = UUID.randomUUID(), variant = UUID.randomUUID(), offer = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyer, "buyer-" + buyer, now));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,?,?,'ACTIVE','APPROVED',?,?,0)", merchant, "Store", "store-" + merchant, timestamp, timestamp);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version) values (?,?,?, ?, ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'), 'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,'VND',?,?,0)", location, merchant, "Main", "1 Street", timestamp, timestamp);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,?,?,'PRODUCT','ACTIVE',0,0)", category, "Food", "food-" + category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',?,?,0)", item, merchant, category, "Rice", "rice-" + item, timestamp, timestamp);
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,?,true,'ACTIVE',?,?,0)", variant, item, "Default", timestamp, timestamp);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values (?,?,?,10000,'VND',true,'TRACKED',?,?,0)", offer, location, variant, timestamp, timestamp);
        InventoryBalanceEntity balance = InventoryBalanceEntity.zero(offer, now); balance.adjust(1, 0, now); balances.saveAndFlush(balance);
        return new Fixture(buyer, merchant, location, item, variant, offer);
    }
    protected record Fixture(UUID buyerId, UUID merchantId, UUID locationId, UUID itemId, UUID variantId, UUID offerId) {}
}
