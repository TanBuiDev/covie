package vn.covie.orders.security;

import java.util.UUID;

public record OrderAccess(UUID actorUserId, OrderActorType actorType, UUID orderId,
                          UUID merchantId, UUID merchantLocationId) {}
