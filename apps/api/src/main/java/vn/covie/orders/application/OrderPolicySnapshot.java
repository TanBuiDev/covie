package vn.covie.orders.application;

import java.time.Duration;

public record OrderPolicySnapshot(
        Duration confirmationTimeout,
        Duration autoCompleteGrace,
        Duration pickupNoShowGrace,
        Duration issueCloseTimeout) {
    public OrderPolicySnapshot {
        requirePositive(confirmationTimeout, "confirmationTimeout");
        requirePositive(autoCompleteGrace, "autoCompleteGrace");
        requirePositive(pickupNoShowGrace, "pickupNoShowGrace");
        requirePositive(issueCloseTimeout, "issueCloseTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
