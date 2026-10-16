package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.time.Instant;

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
    @Test void quoteMustBeCurrentOneUseAndBoundToExactPlanAndVoucher() {
        UUID version = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-16T10:00:00Z");
        var good = new BillingCheckoutService.QuoteSnapshot(version,"SAVE",9000,
            now.plusSeconds(600),null);
        assertDoesNotThrow(()->BillingCheckoutService.requireQuoteValid(good,version,"SAVE",now));
        assertDoesNotThrow(()->BillingCheckoutService.requireQuoteAmount(9000L,9000L));
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteValid(good,UUID.randomUUID(),"SAVE",now));
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteValid(good,version,null,now));
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteValid(good,version,"SAVE",now.plusSeconds(601)));
        var used = new BillingCheckoutService.QuoteSnapshot(version,"SAVE",9000,
            now.plusSeconds(600),"already-used-key");
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteValid(used,version,"SAVE",now));
        assertThrows(RuntimeException.class,()->
            BillingCheckoutService.requireQuoteAmount(9000L,8000L));
    }
}

