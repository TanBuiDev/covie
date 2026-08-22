package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;

public record OrderTimelineEventResponse(UUID id, OrderEventType eventType, UUID actorUserId,
        OrderActorContext actorContext, OrderStatus fromStatus, OrderStatus toStatus, String reasonCode,
        String note, Instant createdAt) {}
