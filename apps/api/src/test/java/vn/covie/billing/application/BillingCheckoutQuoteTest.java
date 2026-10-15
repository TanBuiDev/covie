package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BillingCheckoutQuoteTest {
    @Test void acceptsExactPriceAndRevisionAndLegacyClientsWithoutExpectedFields() {
        assertDoesNotThrow(()->BillingCheckoutService.requireQuoteUnchanged(9000L,3,9000,3));
        assertDoesNotThrow(()->BillingCheckoutService.requireQuoteUnchanged(null,null,9000,3));
    }
    @Test void rejectsChangedAmountOrPlanRevisionBeforeCreatingProviderLink() {
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteUnchanged(9000L,3,10000,3));
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteUnchanged(9000L,3,9000,4));
    }
}
