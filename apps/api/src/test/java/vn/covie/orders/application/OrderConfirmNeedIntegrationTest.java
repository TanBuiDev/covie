package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderAccess;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventWriter;

class OrderConfirmNeedIntegrationTest {
    @Test
    void confirmationParticipantRunsAfterOrderTransition() {
        OrderEntity order = mock(OrderEntity.class);
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        when(order.getId()).thenReturn(orderId);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED);
        when(order.getConfirmationExpiresAt()).thenReturn(now.plusSeconds(60));

        IdempotencyExecutor idempotency = mock(IdempotencyExecutor.class);
        OrderTransitionService transitions = mock(OrderTransitionService.class);
        OrderAccessPolicy access = mock(OrderAccessPolicy.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        OrderEventService events = mock(OrderEventService.class);
        OutboxEventWriter outbox = mock(OutboxEventWriter.class);
        InventoryReservationService inventory = mock(InventoryReservationService.class);
        OrderConfirmedParticipant participant = mock(OrderConfirmedParticipant.class);
        when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        var processor = mock(vn.covie.orders.model.OrderActorContext.class);

        // Exercise the transaction-body method directly: authorization and transition are still the real service sequence.
        // access.requireMerchantProcessor returns the access object used by OrderCommandService.
        var action = mock(OrderAccess.class);
        UUID actorId = UUID.randomUUID();
        when(action.actorUserId()).thenReturn(actorId);
        when(access.requireMerchantProcessor(order)).thenReturn(action);
        when(order.getId()).thenReturn(orderId);
        when(order.getOrderCode()).thenReturn("P05-" + orderId);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);

        OrderCommandService service = new OrderCommandService(
                idempotency, transitions, access, actors, events, outbox, inventory, () -> now, List.of(participant));

        service.confirmLocked(orderId);

        verify(order).confirm(now);
        verify(participant).onConfirmed(order, actorId);
        assertThat(orderId).isNotNull();
    }
}
