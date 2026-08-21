package vn.covie.checkout.application;

import vn.covie.shipping.application.ShippingEvaluation;

public record CheckoutRevalidation(
        RevalidatedCheckout checkout,
        CheckoutComparableSnapshot comparableSnapshot,
        Long shippingFee,
        Long total,
        long knownTotal,
        ShippingEvaluation shipping) {}
