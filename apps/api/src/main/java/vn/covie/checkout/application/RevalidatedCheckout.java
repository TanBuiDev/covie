package vn.covie.checkout.application;

import java.util.List;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shipping.application.ShippingEvaluation;

public record RevalidatedCheckout(
        CheckoutCandidate candidate,
        FulfillmentMethod fulfillmentMethod,
        PaymentMethod paymentMethod,
        List<RevalidatedCheckoutItem> items,
        long subtotal,
        String currency,
        ShippingEvaluation shipping,
        FulfillmentSnapshots fulfillmentSnapshots) {}
