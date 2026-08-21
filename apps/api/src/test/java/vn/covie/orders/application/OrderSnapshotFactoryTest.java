package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.shipping.application.ShippingEvaluation;
import vn.covie.shipping.model.ShippingMode;

class OrderSnapshotFactoryTest {
    private final OrderSnapshotFactory factory = new OrderSnapshotFactory();

    @Test
    void productPickupHasNoShippingFeeAndUsesSubtotalAsTotal() {
        OrderMoney money = factory.money(OrderType.PRODUCT, FulfillmentMethod.PICKUP, 50_000L, null);

        assertThat(money.shippingFeeType()).isEqualTo(OrderShippingFeeType.NOT_APPLICABLE);
        assertThat(money.shippingFee()).isNull();
        assertThat(money.knownTotal()).isEqualTo(50_000L);
        assertThat(money.totalAmount()).isEqualTo(50_000L);
    }

    @Test
    void negotiatedDeliveryLeavesTotalAmountUnknown() {
        ShippingEvaluation shipping = new ShippingEvaluation(ShippingMode.NEGOTIATED, 50_000L, null, null,
                50_000L, "VND", 0, java.util.List.of(), null);

        OrderMoney money = factory.money(OrderType.PRODUCT, FulfillmentMethod.SELLER_DELIVERY, 50_000L, shipping);

        assertThat(money.shippingFeeType()).isEqualTo(OrderShippingFeeType.NEGOTIATED);
        assertThat(money.shippingFee()).isNull();
        assertThat(money.knownTotal()).isEqualTo(50_000L);
        assertThat(money.totalAmount()).isNull();
    }
}
