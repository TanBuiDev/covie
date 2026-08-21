package vn.covie.checkout.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckoutComparisonService {
    public List<CheckoutChange> compare(CheckoutComparableSnapshot accepted, CheckoutComparableSnapshot current) {
        List<CheckoutChange> changes = new ArrayList<>();
        if (!java.util.Objects.equals(accepted.sourceCartVersion(), current.sourceCartVersion())) {
            changes.add(change(CheckoutChangeType.CART_CHANGED));
        }
        if (!java.util.Objects.equals(accepted.addressId(), current.addressId())
                || !java.util.Objects.equals(accepted.addressVersion(), current.addressVersion())) {
            changes.add(change(CheckoutChangeType.ADDRESS_CHANGED));
        }
        if (accepted.fulfillmentMethod() != current.fulfillmentMethod()
                || !accepted.merchantLocationId().equals(current.merchantLocationId())) {
            changes.add(change(CheckoutChangeType.DELIVERY_COVERAGE_CHANGED));
        }

        Map<UUID, CheckoutComparableItem> currentItems = byOffer(current.items());
        boolean cartChanged = false;
        for (CheckoutComparableItem expected : accepted.items()) {
            CheckoutComparableItem actual = currentItems.remove(expected.locationVariantId());
            if (actual == null || expected.quantity() != actual.quantity()) {
                cartChanged = true;
                continue;
            }
            if (!actual.available()) changes.add(change(CheckoutChangeType.ITEM_UNAVAILABLE));
            else if (expected.unitPrice() != actual.unitPrice()) changes.add(change(CheckoutChangeType.PRICE_CHANGED));
        }
        if (!currentItems.isEmpty()) cartChanged = true;
        if (cartChanged && changes.stream().noneMatch(change -> change.type() == CheckoutChangeType.CART_CHANGED)) {
            changes.add(change(CheckoutChangeType.CART_CHANGED));
        }
        if (accepted.subtotal() != current.subtotal() && changes.stream().noneMatch(change -> change.type() == CheckoutChangeType.PRICE_CHANGED)) {
            changes.add(change(CheckoutChangeType.SHIPPING_CHANGED));
        }
        return List.copyOf(changes);
    }

    private static Map<UUID, CheckoutComparableItem> byOffer(List<CheckoutComparableItem> items) {
        Map<UUID, CheckoutComparableItem> values = new LinkedHashMap<>();
        items.forEach(item -> values.put(item.locationVariantId(), item));
        return values;
    }

    private static CheckoutChange change(CheckoutChangeType type) {
        return new CheckoutChange(type, Map.of());
    }
}
