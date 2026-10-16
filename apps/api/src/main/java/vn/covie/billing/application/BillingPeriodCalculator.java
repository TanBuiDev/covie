package vn.covie.billing.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Calendar-month term anchored to UTC, never based on browser clock or floating-point days. */
public final class BillingPeriodCalculator {
    private BillingPeriodCalculator() {}

    public static Instant addMonths(Instant start, int cycleMonths) {
        if (start == null || cycleMonths < 1 || cycleMonths > 120) {
            throw new IllegalArgumentException("Invalid billing cycle");
        }
        LocalDateTime date = LocalDateTime.ofInstant(start, ZoneOffset.UTC);
        return date.plusMonths(cycleMonths).toInstant(ZoneOffset.UTC);
    }
}
