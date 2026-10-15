package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingPeriodCalculatorTest {
    @Test void monthEndAndLeapYear() {
        assertEquals(Instant.parse("2028-02-29T10:00:00Z"),
            BillingPeriodCalculator.addMonths(Instant.parse("2028-01-31T10:00:00Z"), 1));
        assertEquals(Instant.parse("2027-02-28T10:00:00Z"),
            BillingPeriodCalculator.addMonths(Instant.parse("2027-01-31T10:00:00Z"), 1));
    }
    @Test void invalidPeriod() {
        assertThrows(IllegalArgumentException.class,
            () -> BillingPeriodCalculator.addMonths(Instant.now(), 0));
    }
}
