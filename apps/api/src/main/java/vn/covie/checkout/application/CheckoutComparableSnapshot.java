package vn.covie.checkout.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;

public record CheckoutComparableSnapshot(
        CheckoutSourceType sourceType,
        UUID merchantLocationId,
        Long sourceCartVersion,
        FulfillmentMethod fulfillmentMethod,
        UUID addressId,
        long subtotal,
        String currency,
        List<CheckoutComparableItem> items) {
    public CheckoutComparableSnapshot {
        items = items.stream().sorted(Comparator.comparing(CheckoutComparableItem::locationVariantId)).toList();
    }
}
