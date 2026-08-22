package vn.covie.checkout.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutCandidate;
import vn.covie.checkout.application.CheckoutCandidateItem;
import vn.covie.checkout.application.CheckoutComparableItem;
import vn.covie.checkout.application.CheckoutComparableSnapshot;
import vn.covie.checkout.application.CheckoutRevalidation;
import vn.covie.checkout.application.FulfillmentSnapshots;
import vn.covie.checkout.application.RevalidatedCheckout;
import vn.covie.checkout.application.RevalidatedCheckoutItem;
import vn.covie.checkout.model.CheckoutSessionEntity;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.inventory.application.InventoryLockService;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.web.ApiException;
import vn.covie.users.model.UserEntity;

class CheckoutLastStockConcurrencyTest extends CheckoutRollbackInvariantTest {
    @Autowired InventoryLockService locks;
    @Autowired TransactionTemplate transactions;

    @Test
    void twoConcurrentBuyersOfTheLastTrackedUnitYieldOneSuccessAndOneConflict() throws Exception {
        Fixture fixture = fixture();
        CyclicBarrier ready = new CyclicBarrier(2);
        Callable<Boolean> reserve = () -> {
            ready.await(5, TimeUnit.SECONDS);
            try {
                return transactions.execute(status -> {
                    var balance = locks.lockTrackedBalances(Set.of(fixture.offerId())).getFirst();
                    balance.reserve(1, Instant.now());
                    return true;
                });
            } catch (RuntimeException expected) {
                return false;
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = pool.invokeAll(List.of(reserve, reserve));
            long successes = results.stream().filter(result -> get(result)).count();
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select stock_on_hand - reserved_quantity from inventory_balances where location_variant_id = ?", Long.class, fixture.offerId())).isZero();
    }

    @Test
    void twoConcurrentCheckoutCommitsOfTheLastUnitYieldOneOrderAndOneInventoryConflict() throws Exception {
        Fixture fixture = fixture();
        UUID secondBuyer = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(secondBuyer, "buyer-" + secondBuyer, Instant.now()));
        Map<UUID, CheckoutRevalidation> revalidations = Map.of(
                fixture.buyerId(), revalidation(fixture, fixture.buyerId()),
                secondBuyer, revalidation(fixture, secondBuyer));
        Map<String, CheckoutSessionEntity> tokens = new HashMap<>();
        tokens.put("buyer-one", session(fixture.buyerId(), fixture));
        tokens.put("buyer-two", session(secondBuyer, fixture));
        org.mockito.Mockito.when(sessions.lock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> tokens.get(call.getArgument(0)));
        org.mockito.Mockito.when(previews.revalidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> revalidations.get(call.getArgument(0)));
        org.mockito.Mockito.when(previews.revalidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(call -> revalidations.get(call.getArgument(0)));
        org.mockito.Mockito.when(comparison.compare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        CyclicBarrier ready = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = pool.invokeAll(List.of(
                    commit(ready, fixture.buyerId(), "buyer-one"), commit(ready, secondBuyer, "buyer-two")));
            assertThat(results.stream().filter(CheckoutLastStockConcurrencyTest::get).count()).isEqualTo(1);
        }
        Integer orderCount = jdbc.queryForObject("select count(*) from orders where buyer_user_id in (?, ?)",
                (rs, row) -> rs.getInt(1), fixture.buyerId(), secondBuyer);
        assertThat(orderCount).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id = ?", Long.class, fixture.offerId())).isEqualTo(1L);
    }

    @Test
    void reversedTrackedOfferOrderingAcrossConcurrentCommitsDoesNotDeadlock() throws Exception {
        Fixture fixture = fixture();
        TrackedOffer second = secondTrackedOffer(fixture);
        UUID secondBuyer = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(secondBuyer, "buyer-" + secondBuyer, Instant.now()));
        List<CheckoutCandidateItem> buyerOneItems = List.of(item(fixture), item(second));
        List<CheckoutCandidateItem> buyerTwoItems = List.of(item(second), item(fixture));
        Map<UUID, CheckoutRevalidation> revalidations = Map.of(
                fixture.buyerId(), revalidation(fixture, fixture.buyerId(), buyerOneItems),
                secondBuyer, revalidation(fixture, secondBuyer, buyerTwoItems));
        Map<String, CheckoutSessionEntity> tokens = Map.of(
                "ordered", session(fixture.buyerId(), fixture, buyerOneItems),
                "reversed", session(secondBuyer, fixture, buyerTwoItems));
        org.mockito.Mockito.when(sessions.lock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> tokens.get(call.getArgument(0)));
        org.mockito.Mockito.when(previews.revalidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> revalidations.get(call.getArgument(0)));
        org.mockito.Mockito.when(previews.revalidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(call -> revalidations.get(call.getArgument(0)));
        org.mockito.Mockito.when(comparison.compare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        CyclicBarrier ready = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = pool.invokeAll(List.of(
                    commit(ready, fixture.buyerId(), "ordered"), commit(ready, secondBuyer, "reversed")));
            assertThat(results.stream().filter(CheckoutLastStockConcurrencyTest::get).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id in (?, ?)", Integer.class,
                fixture.buyerId(), secondBuyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id = ?", Long.class,
                fixture.offerId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id = ?", Long.class,
                second.offerId())).isEqualTo(1L);
    }

    private Callable<Boolean> commit(CyclicBarrier ready, UUID buyer, String token) {
        return () -> { ready.await(5, TimeUnit.SECONDS); try { commits.commit(buyer, token); return true; }
            catch (ApiException expected) { return false; } };
    }

    private CheckoutSessionEntity session(UUID buyer, Fixture fixture) {
        return session(buyer, fixture, List.of(item(fixture)));
    }

    private CheckoutSessionEntity session(UUID buyer, Fixture fixture, List<CheckoutCandidateItem> items) {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                items.stream().map(candidate -> new CheckoutPreviewRequest.DirectItem(candidate.locationVariantId(), candidate.quantity())).toList(),
                FulfillmentMethod.PICKUP, null, PaymentMethod.COD);
        return CheckoutSessionEntity.create(UUID.randomUUID(), "hash-" + buyer, buyer, CheckoutSourceType.DIRECT,
                mapper.createObjectNode(), null, mapper.valueToTree(request), mapper.valueToTree(snapshot(fixture, items)), Instant.now().plusSeconds(300), Instant.now());
    }

    private CheckoutRevalidation revalidation(Fixture fixture, UUID buyer) {
        return revalidation(fixture, buyer, List.of(item(fixture)));
    }

    private CheckoutRevalidation revalidation(Fixture fixture, UUID buyer, List<CheckoutCandidateItem> items) {
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, buyer, fixture.merchantId(), fixture.locationId(),
                CatalogItemType.PRODUCT, items, null, mapper.createObjectNode());
        RevalidatedCheckout checkout = new RevalidatedCheckout(candidate, FulfillmentMethod.PICKUP, PaymentMethod.COD,
                candidate.items().stream().map(item -> new RevalidatedCheckoutItem(item, 10_000, mapper.createObjectNode())).toList(),
                10_000L * items.size(), "VND", null,
                new FulfillmentSnapshots(null, null, mapper.createObjectNode(), null, mapper.createObjectNode(), mapper.createObjectNode(), null));
        return new CheckoutRevalidation(checkout, snapshot(fixture, items), 0L, 10_000L * items.size(), 10_000L * items.size(), null,
                items.stream().map(CheckoutCandidateItem::locationVariantId).collect(java.util.stream.Collectors.toSet()));
    }

    private CheckoutComparableSnapshot snapshot(Fixture fixture) {
        return snapshot(fixture, List.of(item(fixture)));
    }

    private CheckoutComparableSnapshot snapshot(Fixture fixture, List<CheckoutCandidateItem> items) {
        return new CheckoutComparableSnapshot(CheckoutSourceType.DIRECT, fixture.locationId(), null, FulfillmentMethod.PICKUP,
                null, null, 10_000 * items.size(), 0L, 10_000L * items.size(), 10_000L * items.size(), null, "VND",
                items.stream().map(item -> new CheckoutComparableItem(item.locationVariantId(), item.quantity(), 10_000, true)).toList());
    }

    private CheckoutCandidateItem item(Fixture fixture) {
        return new CheckoutCandidateItem(fixture.offerId(), fixture.variantId(), fixture.itemId(), 1);
    }

    private CheckoutCandidateItem item(TrackedOffer offer) {
        return new CheckoutCandidateItem(offer.offerId(), offer.variantId(), offer.itemId(), 1);
    }

    private TrackedOffer secondTrackedOffer(Fixture fixture) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        UUID variantId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,?,false,'ACTIVE',?,?,0)",
                variantId, fixture.itemId(), "Second", timestamp, timestamp);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values (?,?,?,10000,'VND',true,'TRACKED',?,?,0)",
                offerId, fixture.locationId(), variantId, timestamp, timestamp);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,?,?,?,0)",
                offerId, 1, 0, timestamp);
        return new TrackedOffer(fixture.itemId(), variantId, offerId);
    }

    private record TrackedOffer(UUID itemId, UUID variantId, UUID offerId) {}

    private static boolean get(Future<Boolean> future) {
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
