package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;

public record OrderDetailResponse(UUID id, String orderCode, OrderType type, OrderStatus status, long version,
        UUID buyerUserId, UUID merchantId, UUID merchantLocationId, Set<OrderAction> allowedActions,
        Instant createdAt, Instant confirmedAt, Instant completedAt, Instant closedAt) {}
