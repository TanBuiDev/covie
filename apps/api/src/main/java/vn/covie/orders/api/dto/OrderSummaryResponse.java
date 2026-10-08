package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;

public record OrderSummaryResponse(UUID id, String orderCode, OrderType type, OrderStatus status, Instant createdAt,
        Instant updatedAt, OrderPricingResponse pricing, java.util.List<OrderSummaryItemResponse> items) {}
