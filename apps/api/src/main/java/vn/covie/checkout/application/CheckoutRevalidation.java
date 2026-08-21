package vn.covie.checkout.application;

import vn.covie.shipping.application.ShippingEvaluation;
import java.util.Set;
import java.util.UUID;

public record CheckoutRevalidation(
        RevalidatedCheckout checkout,
        CheckoutComparableSnapshot comparableSnapshot,
        Long shippingFee,
        Long total,
        long knownTotal,
        ShippingEvaluation shipping,
        Set<UUID> trackedOfferIds) {}
