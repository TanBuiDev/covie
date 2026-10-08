package vn.covie.checkout.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.delivery.application.CoverageMatchSource;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.delivery.model.CoverageType;
import vn.covie.location.application.GeoPointFactory;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;

class CheckoutDeliveryCoverageIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    @Autowired DeliveryCoverageEvaluator evaluator;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired GeoPointFactory points;
    @Autowired JdbcTemplate jdbc;

    @Test
    void evaluatesProductRadiusAndServiceAncestorAreaWithPostgisDistance() {
        UUID merchantId = merchants.saveAndFlush(MerchantProfileEntity.create(
                UUID.randomUUID(), "Coverage merchant", "coverage-" + UUID.randomUUID(), NOW)).getId();
        UUID locationId = locations.saveAndFlush(MerchantLocationEntity.create(
                UUID.randomUUID(), merchantId, "Main", "1 Street", 10.8, 106.7, NOW)).getId();
        UUID parentArea = insertArea(null, "Parent");
        UUID childArea = insertArea(parentArea, "Child");
        UUID otherArea = insertArea(null, "Other");

        UUID productCoverage = UUID.randomUUID();
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?, 'PRODUCT_DELIVERY',true,2000,false,'ACTIVE',?,?,0)",
                productCoverage, locationId, Timestamp.from(NOW), Timestamp.from(NOW));
        UUID serviceCoverage = UUID.randomUUID();
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?, 'SERVICE_AREA',false,null,true,'ACTIVE',?,?,0)",
                serviceCoverage, locationId, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("insert into delivery_coverage_areas(delivery_coverage_id,location_area_id) values (?,?)",
                serviceCoverage, parentArea);

        var near = context(10.8, 106.71, childArea);
        var product = evaluator.evaluate(locationId, CoverageType.PRODUCT_DELIVERY, near);
        var service = evaluator.evaluate(locationId, CoverageType.SERVICE_AREA, near);

        assertThat(product.eligible()).isTrue();
        assertThat(product.distanceMeters()).isBetween(1_000L, 1_200L);
        assertThat(product.matchedBy()).containsExactly(CoverageMatchSource.RADIUS);
        assertThat(evaluator.evaluate(locationId, CoverageType.PRODUCT_DELIVERY,
                context(10.8, 106.71, null)).eligible()).isTrue();
        assertThat(service.eligible()).isTrue();
        assertThat(service.distanceMeters()).isEqualTo(product.distanceMeters());
        assertThat(service.matchedBy()).containsExactly(CoverageMatchSource.AREA);

        assertThat(evaluator.evaluate(locationId, CoverageType.PRODUCT_DELIVERY, context(10.8, 106.75, childArea)).eligible()).isFalse();
        assertThat(evaluator.evaluate(locationId, CoverageType.SERVICE_AREA, context(10.8, 106.71, otherArea)).eligible()).isFalse();
    }

    private UUID insertArea(UUID parentId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into location_areas(id,parent_id,area_type,name,slug,status,sort_order,created_at,updated_at,version) values (?,?, 'WARD',?,?, 'ACTIVE',0,?,?,0)",
                id, parentId, name, name.toLowerCase() + "-" + id, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private LocationContext context(double latitude, double longitude, UUID areaId) {
        return new LocationContext(LocationResolutionSource.SAVED_ADDRESS, points.point(latitude, longitude),
                areaId, UUID.randomUUID(), ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
