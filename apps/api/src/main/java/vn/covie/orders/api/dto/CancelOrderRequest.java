package vn.covie.orders.api.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 60) String reasonCode, @Size(max = 1000) String note) {}
