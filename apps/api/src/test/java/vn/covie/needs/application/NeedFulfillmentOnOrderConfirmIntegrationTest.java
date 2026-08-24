package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderSourceType;
import vn.covie.shared.outbox.OutboxEventWriter;

class NeedFulfillmentOnOrderConfirmIntegrationTest {
    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");
    private final NeedRequestRepository needs = mock(NeedRequestRepository.class);
    private final NeedResponseRepository responses = mock(NeedResponseRepository.class);
    private final NeedResponseItemRepository items = mock(NeedResponseItemRepository.class);
    private final OutboxEventWriter outbox = mock(OutboxEventWriter.class);
    private final NeedFulfillmentOnOrderConfirm participant =
            new NeedFulfillmentOnOrderConfirm(needs, responses, items, outbox, () -> now);

    @Test
    void activeNeedIsFulfilledByFirstConfirmedSourceOrder() {
        UUID orderId = UUID.randomUUID();
        NeedRequestEntity need = activeNeed(now.plusSeconds(3600));
        OrderEntity order = sourceOrder(orderId, need);
        stubSourceChain(order, need);

        participant.onConfirmed(order, UUID.randomUUID());

        assertThat(need.getRequestStatus()).isEqualTo(NeedStatus.FULFILLED);
        assertThat(need.getFulfilledOrderId()).isEqualTo(orderId);
        assertThat(need.getClosedAt()).isEqualTo(now);
        verify(outbox).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleActiveNeedExpiresInsteadOfBeingFulfilled() {
        NeedRequestEntity need = activeNeed(now.minusSeconds(1));
        OrderEntity order = sourceOrder(UUID.randomUUID(), need);
        stubSourceChain(order, need);

        participant.onConfirmed(order, UUID.randomUUID());

        assertThat(need.getRequestStatus()).isEqualTo(NeedStatus.EXPIRED);
        assertThat(need.getFulfilledOrderId()).isNull();
        verify(outbox).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void laterConfirmationDoesNotOverwriteFirstFulfilledOrder() {
        UUID firstOrderId = UUID.randomUUID();
        NeedRequestEntity need = activeNeed(now.plusSeconds(3600));
        need.fulfill(firstOrderId, now.minusSeconds(10));
        OrderEntity laterOrder = sourceOrder(UUID.randomUUID(), need);
        stubSourceChain(laterOrder, need);

        participant.onConfirmed(laterOrder, UUID.randomUUID());

        assertThat(need.getFulfilledOrderId()).isEqualTo(firstOrderId);
        verify(outbox, never()).append(org.mockito.ArgumentMatchers.any());
    }

    private void stubSourceChain(OrderEntity order, NeedRequestEntity need) {
        UUID itemId = order.getSourceNeedResponseItemId();
        UUID responseId = UUID.randomUUID();
        NeedResponseItemEntity item = mock(NeedResponseItemEntity.class);
        NeedResponseEntity response = mock(NeedResponseEntity.class);
        when(needs.lockById(need.getId())).thenReturn(Optional.of(need));
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(item.getNeedResponseId()).thenReturn(responseId);
        when(responses.findById(responseId)).thenReturn(Optional.of(response));
        when(response.getNeedRequestId()).thenReturn(need.getId());
    }

    private OrderEntity sourceOrder(UUID orderId, NeedRequestEntity need) {
        OrderEntity order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getSourceType()).thenReturn(OrderSourceType.NEED_REQUEST);
        when(order.getSourceNeedRequestId()).thenReturn(need.getId());
        when(order.getSourceNeedResponseItemId()).thenReturn(UUID.randomUUID());
        return order;
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
