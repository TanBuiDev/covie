package vn.covie.checkout.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

    private Callable<Boolean> commit(CyclicBarrier ready, UUID buyer, String token) {
        return () -> { ready.await(5, TimeUnit.SECONDS); try { commits.commit(buyer, token); return true; }
            catch (ApiException expected) { return false; } };
    }

    private CheckoutSessionEntity session(UUID buyer, Fixture fixture) {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(fixture.offerId(), 1)), FulfillmentMethod.PICKUP, null, PaymentMethod.COD);
        return CheckoutSessionEntity.create(UUID.randomUUID(), "hash-" + buyer, buyer, CheckoutSourceType.DIRECT,
                mapper.createObjectNode(), null, mapper.valueToTree(request), mapper.valueToTree(snapshot(fixture)), Instant.now().plusSeconds(300), Instant.now());
    }

    private CheckoutRevalidation revalidation(Fixture fixture, UUID buyer) {
        CheckoutCandidate candidate = new CheckoutCandidate(CheckoutSourceType.DIRECT, buyer, fixture.merchantId(), fixture.locationId(),
                CatalogItemType.PRODUCT, List.of(new CheckoutCandidateItem(fixture.offerId(), fixture.variantId(), fixture.itemId(), 1)), null, mapper.createObjectNode());
        RevalidatedCheckout checkout = new RevalidatedCheckout(candidate, FulfillmentMethod.PICKUP, PaymentMethod.COD,
                List.of(new RevalidatedCheckoutItem(candidate.items().getFirst(), 10_000, mapper.createObjectNode())), 10_000, "VND", null,
                new FulfillmentSnapshots(null, null, mapper.createObjectNode(), null, mapper.createObjectNode(), mapper.createObjectNode(), null));
        return new CheckoutRevalidation(checkout, snapshot(fixture), 0L, 10_000L, 10_000L, null, Set.of(fixture.offerId()));
    }

    private CheckoutComparableSnapshot snapshot(Fixture fixture) {
        return new CheckoutComparableSnapshot(CheckoutSourceType.DIRECT, fixture.locationId(), null, FulfillmentMethod.PICKUP,
                null, null, 10_000, 0L, 10_000L, 10_000L, null, "VND", List.of(new CheckoutComparableItem(fixture.offerId(), 1, 10_000, true)));
    }

    private static boolean get(Future<Boolean> future) {
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
