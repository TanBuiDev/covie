package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.api.dto.CancelOrderRequest;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.orders.persistence.OrderCancellationRequestRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;

class OrderPreConfirmationCancelTest {
    @Test
    void buyerCancelsPendingOrderAndReleasesReservation() {
        UUID buyer = UUID.randomUUID(), orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "CVTEST", OrderType.PRODUCT, buyer, UUID.randomUUID(),
                UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE, 0L, 10_000, 10_000L,
                "VND", Instant.parse("2026-08-22T00:30:00Z"), Instant.parse("2026-08-22T00:00:00Z"));
        IdempotencyExecutor idem = mock(IdempotencyExecutor.class);
        doAnswer(this::execute).when(idem).execute(any(), any(), any(), any(), any(Integer.class), any(), any());
        OrderTransitionService transitions = mock(OrderTransitionService.class); when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class); when(access.requireBuyer(order)).thenReturn(
                new OrderAccess(buyer, OrderActorType.BUYER, orderId, order.getMerchantProfileId(), order.getMerchantLocationId()));
        CurrentActorProvider actors = mock(CurrentActorProvider.class); when(actors.currentActor()).thenReturn(new CurrentActor(buyer, Set.of(SystemRole.USER), UUID.randomUUID()));
        OrderCommandService service = new OrderCommandService(idem, transitions, access, actors, mock(OrderEventService.class),
                mock(OutboxEventWriter.class), mock(InventoryReservationService.class), () -> Instant.parse("2026-08-22T00:01:00Z"));

        service.cancel(orderId, "key-cancel", new CancelOrderRequest(null, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED_BEFORE_CONFIRMATION);
    }

    @Test
    void merchantCancelsConfirmedOrderAndReleasesReservation() {
        UUID merchant = UUID.randomUUID(), orderId = UUID.randomUUID(); Instant now=Instant.parse("2026-08-22T00:01:00Z");
        OrderEntity order = OrderEntity.pending(orderId, "CVSELL", OrderType.PRODUCT, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE, 0L, 10_000, 10_000L, "VND", now.plusSeconds(60), now.minusSeconds(10));
        order.confirm(now.minusSeconds(1));
        OrderTransitionService transitions=mock(OrderTransitionService.class); when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        OrderAccessPolicy access=mock(OrderAccessPolicy.class); OrderAccess merchantAccess=new OrderAccess(merchant,OrderActorType.MERCHANT,orderId,order.getMerchantProfileId(),order.getMerchantLocationId());
        when(access.requireVisible(order)).thenReturn(merchantAccess); when(access.requireMerchantProcessor(order)).thenReturn(merchantAccess);
        InventoryReservationService inventory=mock(InventoryReservationService.class);
        OrderCancellationService service=new OrderCancellationService(transitions,access,mock(OrderCancellationRequestRepository.class),inventory,mock(OrderEventService.class),mock(OutboxEventWriter.class),mock(UuidV7Generator.class),()->now);

        var result=service.cancel(orderId,new CancelOrderRequest("SELLER_UNAVAILABLE","Closed today"));

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION);
        verify(inventory).releaseActiveForOrder(orderId,merchant,"SELLER_UNAVAILABLE",now);
    }

    @SuppressWarnings("unchecked") private Object execute(InvocationOnMock call) {
        return ((java.util.function.Supplier<Object>) call.getArgument(6)).get();
    }
}
