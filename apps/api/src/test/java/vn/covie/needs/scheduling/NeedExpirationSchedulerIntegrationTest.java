package vn.covie.needs.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import vn.covie.needs.application.NeedScheduledTransitionService;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.outbox.OutboxEventWriter;

class NeedExpirationSchedulerIntegrationTest {
    @Test
    void expiresOnlyRowsClaimedBySkipLockedRepositoryQuery() {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        NeedRequestRepository needs = mock(NeedRequestRepository.class);
        OutboxEventWriter outbox = mock(OutboxEventWriter.class);
        NeedRequestEntity need = activeNeed(now);
        when(needs.claimExpiredBatch(now, 50)).thenReturn(List.of(need));
        NeedScheduledTransitionService service = new NeedScheduledTransitionService(needs, outbox, () -> now);

        int processed = service.expireDueBatch(50);

        assertThat(processed).isEqualTo(1);
        assertThat(need.getRequestStatus()).isEqualTo(NeedStatus.EXPIRED);
        assertThat(need.getClosedAt()).isEqualTo(now);
        verify(needs).claimExpiredBatch(now, 50);
        verify(outbox).append(org.mockito.ArgumentMatchers.any());
    }

    private static NeedRequestEntity activeNeed(Instant now) {
        Point point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        return NeedRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), NeedType.PRODUCT, UUID.randomUUID(),
                "Need", "query", null, 1L, null, point,
                new ObjectMapper().createObjectNode(), 5000, now.minusSeconds(1), now.minusSeconds(100));
    }
}
