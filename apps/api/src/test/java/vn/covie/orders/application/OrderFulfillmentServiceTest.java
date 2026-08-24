package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.outbox.OutboxEventWriter;

class OrderFulfillmentServiceTest {
    @Test
    void sellerCanMarkConfirmedDeliveryOrderOutForDelivery() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "CVTEST", OrderType.PRODUCT, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE, 0L, 10_000, 10_000L, "VND",
                Instant.now().plusSeconds(1800), Instant.now());
        order.confirm(Instant.now());
        ProductFulfillmentEntity fulfillment = ProductFulfillmentEntity.create(orderId, ProductFulfillmentMethod.SELLER_DELIVERY,
                UUID.randomUUID(), UUID.randomUUID(), 100, JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(), null);
        OrderTransitionService transitions = mock(OrderTransitionService.class); when(transitions.lock(orderId)).thenReturn(new LockedOrder(order));
        OrderAccessPolicy access = mock(OrderAccessPolicy.class); when(access.requireMerchantProcessor(order)).thenReturn(new OrderAccess(UUID.randomUUID(),
                OrderActorType.MERCHANT, orderId, order.getMerchantProfileId(), order.getMerchantLocationId()));
        ProductFulfillmentRepository products = mock(ProductFulfillmentRepository.class); when(products.findById(orderId)).thenReturn(Optional.of(fulfillment));
        OrderFulfillmentService service = new OrderFulfillmentService(transitions, access, products, mock(vn.covie.orders.persistence.ServiceFulfillmentRepository.class),
                mock(OrderPolicyProvider.class), mock(OrderEventService.class), mock(OutboxEventWriter.class), () -> Instant.parse("2026-08-22T01:00:00Z"));

        service.markOutForDelivery(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(fulfillment.getOutForDeliveryAt()).isEqualTo(Instant.parse("2026-08-22T01:00:00Z"));
    }
}
