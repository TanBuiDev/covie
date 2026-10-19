package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BillingCatalogIfMatchTest {
    @Test void acceptsExactlyOneQuotedPositiveRevision() {
        assertEquals(1,BillingCatalogService.parseIfMatch("\"1\""));
        assertEquals(321,BillingCatalogService.parseIfMatch("\"321\""));
        for(String invalid : new String[]{"1","W/\"1\"","\"\"","\"0\"",
                "\"0001\"","\"-1\"","\"1\" , \"2\"","\"9999999999\""}) {
            assertThrows(RuntimeException.class, ()->BillingCatalogService.parseIfMatch(invalid),invalid);
        }
        assertThrows(RuntimeException.class,()->BillingCatalogService.parseIfMatch(null));
    }
}
