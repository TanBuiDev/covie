package vn.covie.orders.application;

import java.util.UUID;
import vn.covie.orders.model.OrderStatus;

public record OrderChangedPayload(UUID orderId, String orderCode, OrderStatus status) {}
