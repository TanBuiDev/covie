package vn.covie.checkout.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.orders.model.OrderStatus;

public record OrderCreatedResponse(UUID id, String orderCode, OrderStatus status, Instant createdAt) {}
