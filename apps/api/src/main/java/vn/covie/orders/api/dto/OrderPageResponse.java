package vn.covie.orders.api.dto;

import java.util.List;

public record OrderPageResponse(List<OrderSummaryResponse> items, String nextCursor) {}
