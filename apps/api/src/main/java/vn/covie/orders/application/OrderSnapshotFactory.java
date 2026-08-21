package vn.covie.orders.application;

import org.springframework.stereotype.Component;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.shipping.application.ShippingEvaluation;

@Component
public class OrderSnapshotFactory {
    public OrderMoney money(OrderType orderType, FulfillmentMethod fulfillmentMethod, long subtotal,
            ShippingEvaluation shipping) {
        if (orderType == OrderType.SERVICE || fulfillmentMethod == FulfillmentMethod.PICKUP) {
            return new OrderMoney(OrderShippingFeeType.NOT_APPLICABLE, null, subtotal, subtotal);
        }
        if (fulfillmentMethod != FulfillmentMethod.SELLER_DELIVERY || shipping == null) {
            throw new IllegalArgumentException("Seller delivery requires a shipping evaluation.");
        }
        OrderShippingFeeType feeType = OrderShippingFeeType.valueOf(shipping.mode().name());
        return new OrderMoney(feeType, shipping.shippingFeeMinor(), shipping.knownTotalMinor(), shipping.totalMinor());
    }
}
