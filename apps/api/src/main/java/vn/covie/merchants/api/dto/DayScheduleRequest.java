package vn.covie.merchants.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import vn.covie.merchants.model.OperationType;

public record DayScheduleRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull OperationType operationType,
        boolean closed,
        @NotNull List<@NotNull @Valid TimeIntervalRequest> intervals) {}
