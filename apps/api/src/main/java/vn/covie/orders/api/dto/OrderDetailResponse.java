package vn.covie.orders.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.OrderSourceType;
import vn.covie.shared.commerce.PaymentMethod;

public record OrderDetailResponse(UUID id, String orderCode, OrderType type, OrderStatus status, long version,
        OrderSourceType source, OrderPartyResponse buyer, OrderPartyResponse merchant, List<OrderItemResponse> items,
        OrderPricingResponse pricing, OrderFulfillmentResponse fulfillment, PaymentMethod paymentMethod,
        OrderCancellationResponse cancellation, OrderIssueResponse issue, List<OrderTimelineEventResponse> timeline,
        Set<OrderAction> allowedActions,
        Instant createdAt, Instant confirmedAt, Instant completedAt, Instant closedAt) {}
