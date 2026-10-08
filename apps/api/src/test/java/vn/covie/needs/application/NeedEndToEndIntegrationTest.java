package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import vn.covie.needs.api.dto.NeedAllowedAction;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

class NeedEndToEndIntegrationTest {
    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void acceptanceTransitionsAreTerminalAndMutuallyExclusive() {
        NeedRequestEntity cancelled = activeNeed(now.plusSeconds(3600));
        cancelled.cancel(now);
        assertThat(cancelled.getRequestStatus()).isEqualTo(NeedStatus.CANCELLED);
        assertThat(cancelled.getClosedAt()).isEqualTo(now);
        assertThat(cancelled.getFulfilledOrderId()).isNull();
        assertThatThrownBy(() -> cancelled.fulfill(UUID.randomUUID(), now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        NeedRequestEntity expired = activeNeed(now.plusSeconds(3600));
        expired.expire(now);
        assertThat(expired.getRequestStatus()).isEqualTo(NeedStatus.EXPIRED);
        assertThat(expired.getFulfilledOrderId()).isNull();
        assertThatThrownBy(() -> expired.fulfill(UUID.randomUUID(), now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        NeedRequestEntity fulfilled = activeNeed(now.plusSeconds(3600));
        UUID orderId = UUID.randomUUID();
        fulfilled.fulfill(orderId, now);
        assertThat(fulfilled.getRequestStatus()).isEqualTo(NeedStatus.FULFILLED);
        assertThat(fulfilled.getFulfilledOrderId()).isEqualTo(orderId);
        assertThat(fulfilled.getClosedAt()).isEqualTo(now);
    }

    @Test
    void expireIsIdempotentForAlreadyTerminalNeed() {
        NeedRequestEntity need = activeNeed(now.plusSeconds(3600));
        UUID orderId = UUID.randomUUID();
        need.fulfill(orderId, now);

        need.expire(now.plusSeconds(10));

        assertThat(need.getRequestStatus()).isEqualTo(NeedStatus.FULFILLED);
        assertThat(need.getFulfilledOrderId()).isEqualTo(orderId);
        assertThat(need.getClosedAt()).isEqualTo(now);
    }

    @Test
    void detailExposesCancelOnlyWhileTheCancelCommandCanAcceptIt() {
        NeedRequestEntity active = activeNeed(now.plusSeconds(3600));
        NeedRequestEntity expiredByTime = activeNeed(now.minusSeconds(1));
        NeedRequestEntity cancelled = activeNeed(now.plusSeconds(3600));
        cancelled.cancel(now);

        assertThat(NeedCommandService.detail(active, now, 0).allowedActions())
                .containsExactly(NeedAllowedAction.CANCEL);
        assertThat(NeedCommandService.detail(expiredByTime, now, 0).allowedActions()).isEmpty();
        assertThat(NeedCommandService.detail(cancelled, now, 0).allowedActions()).isEmpty();
    }

    private NeedRequestEntity activeNeed(Instant expiresAt) {
        Point point = new GeometryFactory().createPoint(new Coordinate(106.7, 10.8));
        point.setSRID(4326);
        return NeedRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), NeedType.PRODUCT, UUID.randomUUID(),
                "Need", "query", null, 1L, null, point,
                new ObjectMapper().createObjectNode(), 5000, expiresAt, now.minusSeconds(100));
    }
}
