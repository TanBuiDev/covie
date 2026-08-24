package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderStatus;

public record OrderCommandResult(UUID id, String orderCode, OrderStatus status, long version,
        Instant updatedAt, Instant confirmedAt, Instant completedAt, Instant closedAt,
        Set<OrderAction> allowedActions) {}
