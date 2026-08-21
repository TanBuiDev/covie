package vn.covie.checkout.application;

import java.util.Map;

public record CheckoutChange(CheckoutChangeType type, Map<String, Object> details) {
    public CheckoutChange {
        details = Map.copyOf(details);
    }
}
