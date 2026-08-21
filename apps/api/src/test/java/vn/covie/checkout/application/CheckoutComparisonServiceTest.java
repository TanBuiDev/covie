package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;

class CheckoutComparisonServiceTest {
    private static final UUID LOCATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    @Test
    void reportsStablePriceChangeWhenOnlyCurrentUnitPriceChanged() {
        UUID offer = UUID.randomUUID();
        CheckoutComparableSnapshot accepted = snapshot(offer, 50_000L, 1L);
        CheckoutComparableSnapshot current = snapshot(offer, 55_000L, 1L);

        List<CheckoutChange> changes = new CheckoutComparisonService().compare(accepted, current);

        assertThat(changes).extracting(CheckoutChange::type).containsExactly(CheckoutChangeType.PRICE_CHANGED);
    }

    @Test
    void reportsCartChangeWhenCartVersionChanged() {
        UUID offer = UUID.randomUUID();

        List<CheckoutChange> changes = new CheckoutComparisonService().compare(snapshot(offer, 50_000L, 1L),
                snapshot(offer, 50_000L, 2L));

        assertThat(changes).extracting(CheckoutChange::type).containsExactly(CheckoutChangeType.CART_CHANGED);
    }

    private static CheckoutComparableSnapshot snapshot(UUID offerId, long price, Long cartVersion) {
        return new CheckoutComparableSnapshot(CheckoutSourceType.CART, LOCATION_ID, cartVersion,
                FulfillmentMethod.PICKUP, null, null, 50_000L, "VND", List.of(new CheckoutComparableItem(offerId, 1, price, true)));
    }
}
