package vn.covie.admin.api.dto;
import java.time.Instant; import java.util.UUID; import vn.covie.orders.model.*;
public record AdminOrderSummaryResponse(UUID id,String orderCode,OrderType type,OrderStatus status,UUID buyerUserId,UUID merchantId,Instant createdAt,Instant updatedAt) {}
