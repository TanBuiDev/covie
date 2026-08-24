package vn.covie.performance;

import org.junit.jupiter.api.Test;
import vn.covie.checkout.concurrency.CheckoutLastStockConcurrencyTest;

class CheckoutSoakConcurrencyTest extends CheckoutLastStockConcurrencyTest {
    @Test
    void repeatsTheLastStockCheckoutRaceOneHundredTimesWithoutOversellOrDeadlock() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            runLastStockCheckoutRace();
        }
    }

    @Test
    void commitsTwentyIndependentTrackedCheckoutsInParallel() throws Exception {
        runIndependentCheckoutBatch(20);
    }
}
