package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class OrderTransitionServiceTest {
    @Test
    void rejectsACommandWhenTheLockedOrderIsNotInAnAllowedState() {
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVTEST", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", Instant.now().plusSeconds(60), Instant.now());
        order.transitionTo(OrderStatus.CANCELLED_BEFORE_CONFIRMATION, Instant.now());

        assertThatThrownBy(() -> new OrderTransitionService(null).requireStatus(order, OrderStatus.PENDING_CONFIRMATION))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.ORDER_STATE_CONFLICT);
    }
}
