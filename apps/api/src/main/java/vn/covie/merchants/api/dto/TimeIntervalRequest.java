package vn.covie.merchants.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record TimeIntervalRequest(@NotNull LocalTime openTime, @NotNull LocalTime closeTime) {}
