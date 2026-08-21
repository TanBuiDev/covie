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
        Long addressVersion,
        long subtotal,
        Long shippingFee,
        Long total,
        long knownTotal,
        Long minimumDeliveryOrder,
        String currency,
        List<CheckoutComparableItem> items) {
    public CheckoutComparableSnapshot {
        items = items.stream().sorted(Comparator.comparing(CheckoutComparableItem::locationVariantId)).toList();
    }
}
