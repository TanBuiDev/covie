package vn.covie.delivery.application;

import java.util.Set;
import java.util.UUID;
import vn.covie.delivery.model.CoverageType;

public record CoverageEvaluation(boolean eligible, CoverageType coverageType, Long distanceMeters,
        Set<CoverageMatchSource> matchedBy, UUID coverageId) {
    public static CoverageEvaluation notEligible(CoverageType type, Long distanceMeters) {
        return new CoverageEvaluation(false, type, distanceMeters, Set.of(), null);
    }
}
