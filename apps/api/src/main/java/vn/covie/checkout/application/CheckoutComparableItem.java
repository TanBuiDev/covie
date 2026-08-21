package vn.covie.checkout.application;

import java.util.UUID;

public record CheckoutComparableItem(UUID locationVariantId, long quantity, long unitPrice, boolean available) {}
