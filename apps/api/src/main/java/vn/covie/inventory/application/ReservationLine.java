package vn.covie.inventory.application;

import java.util.UUID;

public record ReservationLine(UUID locationVariantId, UUID orderId, UUID orderItemId, long quantity) {}
