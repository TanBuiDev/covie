package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderSummaryResponse(UUID id,String orderCode,String orderType,String status,UUID buyerUserId,UUID merchantId,UUID merchantLocationId,long knownTotal,String currencyCode,Instant createdAt,Instant updatedAt) {}
