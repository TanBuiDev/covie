package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.outbox.OutboxEventWriter;

class OrderCompletionServiceTest {
    @Test
    void buyerCompletesConfirmedProductAndCommitsTheActiveReservation() {
        UUID orderId=UUID.randomUUID(), buyer=UUID.randomUUID();
        OrderEntity order=OrderEntity.pending(orderId,"CVTEST",OrderType.PRODUCT,buyer,UUID.randomUUID(),UUID.randomUUID(),
                PaymentMethod.COD,10000,OrderShippingFeeType.FREE,0L,10000,10000L,"VND",Instant.now().plusSeconds(60),Instant.now());
        order.confirm(Instant.now());
        OrderTransitionService transitions=mock(OrderTransitionService.class); when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        OrderAccessPolicy access=mock(OrderAccessPolicy.class); when(access.requireBuyer(order)).thenReturn(new OrderAccess(buyer,OrderActorType.BUYER,orderId,order.getMerchantProfileId(),order.getMerchantLocationId()));
        InventoryReservationService inventory=mock(InventoryReservationService.class);
        OrderCompletionService service=new OrderCompletionService(transitions,access,inventory,mock(OrderEventService.class),mock(OutboxEventWriter.class),()->Instant.parse("2026-08-22T01:00:00Z"));

        service.confirmReceived(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(inventory).commitActiveForOrder(orderId,buyer,Instant.parse("2026-08-22T01:00:00Z"));
    }
}
