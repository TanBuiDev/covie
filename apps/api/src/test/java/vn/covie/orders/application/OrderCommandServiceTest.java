package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.orders.api.dto.OrderCommandResult;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;

class OrderCommandServiceTest {
    @Test
    void merchantConfirmationChangesOnlyAPendingOrderAndReturnsTheNewState() {
        UUID merchantUser = UUID.randomUUID(); UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "CVTEST", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", Instant.parse("2026-08-22T00:30:00Z"), Instant.parse("2026-08-22T00:00:00Z"));
        IdempotencyExecutor idempotency = mock(IdempotencyExecutor.class);
        doAnswer(this::executeCommand).when(idempotency).execute(any(), any(), any(), any(), any(Integer.class), any(), any());
        OrderTransitionService transitions = mock(OrderTransitionService.class);
        when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class);
        when(access.requireMerchantProcessor(order)).thenReturn(new OrderAccess(merchantUser, OrderActorType.MERCHANT,
                orderId, order.getMerchantProfileId(), order.getMerchantLocationId()));
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(merchantUser, java.util.Set.of(SystemRole.USER), UUID.randomUUID()));
        OrderCommandService service = new OrderCommandService(idempotency, transitions, access, actors,
                mock(OrderEventService.class), mock(OutboxEventWriter.class), () -> Instant.parse("2026-08-22T00:01:00Z"));

        OrderCommandResult result = service.confirm(orderId, "key-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.confirmedAt()).isEqualTo(Instant.parse("2026-08-22T00:01:00Z"));
    }

    @SuppressWarnings("unchecked")
    private Object executeCommand(InvocationOnMock invocation) {
        return ((java.util.function.Supplier<Object>) invocation.getArgument(6)).get();
    }
}
