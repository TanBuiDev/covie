package vn.covie.merchants.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceLocationHoursRequest(@NotNull List<@NotNull @Valid DayScheduleRequest> days) {}
