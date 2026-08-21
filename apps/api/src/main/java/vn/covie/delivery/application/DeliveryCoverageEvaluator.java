package vn.covie.delivery.application;

import java.util.EnumSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.covie.delivery.model.CoverageType;
import vn.covie.delivery.persistence.JdbcCoverageEvaluationRepository;
import vn.covie.location.application.LocationContext;

@Service
public class DeliveryCoverageEvaluator {
    private final JdbcCoverageEvaluationRepository repository;
    public DeliveryCoverageEvaluator(JdbcCoverageEvaluationRepository repository) { this.repository = repository; }

    public CoverageEvaluation evaluate(UUID merchantLocationId, CoverageType type, LocationContext buyerLocation) {
        var match = repository.find(merchantLocationId, type.name(), buyerLocation.point().getY(), buyerLocation.point().getX(), buyerLocation.locationAreaId());
        if (match.isEmpty()) return CoverageEvaluation.notEligible(type, null);
        var value = match.get();
        long distance = value.distanceMeters();
        var sources = EnumSet.noneOf(CoverageMatchSource.class);
        if (value.radiusEnabled() && value.radiusMeters() != null && distance <= value.radiusMeters()) sources.add(CoverageMatchSource.RADIUS);
        if (value.areaEnabled() && value.areaMatch()) sources.add(CoverageMatchSource.AREA);
        if (sources.isEmpty()) return CoverageEvaluation.notEligible(type, distance);
        return new CoverageEvaluation(true, type, distance, SetCopy.copyOf(sources), value.coverageId());
    }

    private static final class SetCopy {
        private static <T> java.util.Set<T> copyOf(java.util.Set<T> values) { return java.util.Set.copyOf(values); }
    }
}
