package vn.covie.needs.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

class NeedRequestEntityTest {
    @Test
    void createsActiveNeedAndCancelsIt() {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        NeedRequestEntity need = NeedRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), NeedType.PRODUCT, UUID.randomUUID(), "  Rice  ", "  jasmine  ",
                " 10kg ", 2L, null,
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new org.locationtech.jts.geom.Coordinate(106.7, 10.7)),
                new ObjectMapper().createObjectNode(), 1_000, now.plusSeconds(3600), now);

        need.cancel(now.plusSeconds(1));

        assertThat(need.getRequestStatus()).isEqualTo(NeedStatus.CANCELLED);
        assertThat(need.getClosedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(need.getTitle()).isEqualTo("Rice");
        assertThat(need.getQueryText()).isEqualTo("jasmine");
    }
}
