package vn.covie.orders.api.dto;

import jakarta.validation.constraints.Size;

public record CancellationResponseBody(@Size(max = 1000) String note) {}
