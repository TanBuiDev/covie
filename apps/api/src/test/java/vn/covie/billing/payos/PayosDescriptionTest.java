package vn.covie.billing.payos;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PayosDescriptionTest {
    @Test void payosBankDescriptionRemainsWithinNineCharactersAndUnique() {
        String first = PayosGateway.paymentDescription(4_000_000_000L);
        String second = PayosGateway.paymentDescription(4_000_000_001L);
        assertTrue(first.matches("CV[A-Z0-9]{1,7}"));
        assertTrue(first.length() <= 9);
        assertNotEquals(first,second);
        assertThrows(IllegalArgumentException.class,()->PayosGateway.paymentDescription(0));
        assertThrows(IllegalArgumentException.class,()->PayosGateway.paymentDescription(Long.MAX_VALUE));
    }
}
