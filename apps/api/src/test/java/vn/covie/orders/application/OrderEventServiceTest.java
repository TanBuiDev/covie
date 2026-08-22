package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.id.UuidV7Generator;

class OrderEventServiceTest {
    @Test
    void appendsAnImmutableLifecycleEvent() {
        OrderEventRepository repository = mock(OrderEventRepository.class);
        OrderEventService service = new OrderEventService(repository, new UuidV7Generator(), new ObjectMapper(),
                () -> Instant.parse("2026-08-22T01:00:00Z"));
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVTEST", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", Instant.now().plusSeconds(1800), Instant.now());

        service.append(order, OrderEventType.SELLER_CONFIRMED, UUID.randomUUID(), OrderActorContext.MERCHANT,
                OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, null, null, Map.of("source", "test"));

        ArgumentCaptor<OrderEventEntity> captured = ArgumentCaptor.forClass(OrderEventEntity.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getEventType()).isEqualTo(OrderEventType.SELLER_CONFIRMED);
        assertThat(captured.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(captured.getValue().getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
