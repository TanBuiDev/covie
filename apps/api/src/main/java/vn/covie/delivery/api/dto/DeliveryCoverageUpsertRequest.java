package vn.covie.delivery.api.dto;

import java.util.Set;
import java.util.UUID;
import vn.covie.delivery.model.CoverageStatus;
import vn.covie.delivery.model.CoverageType;

public record DeliveryCoverageUpsertRequest(CoverageType coverageType, boolean radiusEnabled, Integer radiusMeters,
        boolean areaEnabled, Set<UUID> locationAreaIds, CoverageStatus status) {}
