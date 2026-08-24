package vn.covie.needs.api.dto;

import java.util.UUID;

public record NeedLocationSummaryResponse(UUID addressId, UUID areaId, String timezone) {}
