package vn.covie.checkout.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.inventory.application.InventoryLockService;

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

    private static boolean get(Future<Boolean> future) {
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
