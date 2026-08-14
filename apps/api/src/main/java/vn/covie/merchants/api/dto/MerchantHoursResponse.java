package vn.covie.merchants.api.dto;

import java.util.List;
import java.util.UUID;

public record MerchantHoursResponse(UUID merchantLocationId, List<DayScheduleRequest> days) {}
