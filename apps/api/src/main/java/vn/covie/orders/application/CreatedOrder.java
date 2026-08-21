package vn.covie.orders.application;

import java.util.Map;
import java.util.UUID;

public record CreatedOrder(UUID id, String orderCode, Map<UUID, UUID> orderItemIdsByLocationVariant) {}
