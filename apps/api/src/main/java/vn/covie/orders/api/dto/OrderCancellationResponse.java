package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.CancellationRequestStatus;
import vn.covie.orders.model.OrderStatus;

public record OrderCancellationResponse(UUID id, UUID requestedByUserId, String reasonCode, String reasonText,
        CancellationRequestStatus status, OrderStatus previousStatus, UUID respondedByUserId, String responseNote,
        Instant createdAt, Instant respondedAt) {}
