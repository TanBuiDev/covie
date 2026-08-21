package vn.covie.delivery.api.dto;

import java.util.Set;
import java.util.UUID;
import vn.covie.delivery.model.CoverageStatus;
import vn.covie.delivery.model.CoverageType;

public record DeliveryCoverageResponse(UUID id, UUID merchantLocationId, CoverageType coverageType,
        boolean radiusEnabled, Integer radiusMeters, boolean areaEnabled, Set<UUID> locationAreaIds,
        CoverageStatus status, long version) {}
