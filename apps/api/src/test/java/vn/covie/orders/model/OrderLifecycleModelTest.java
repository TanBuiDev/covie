package vn.covie.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OrderLifecycleModelTest {

    @Test
    void exposesTheCanonicalV1LifecycleVocabulary() {
        assertThat(Arrays.asList(OrderStatus.values())).containsExactly(
                OrderStatus.PENDING_CONFIRMATION,
                OrderStatus.CONFIRMED,
                OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.READY_FOR_PICKUP,
                OrderStatus.AWAITING_BUYER_CONFIRMATION,
                OrderStatus.CANCELLATION_REQUESTED,
                OrderStatus.DELIVERY_ISSUE_REPORTED,
                OrderStatus.SERVICE_ISSUE_REPORTED,
                OrderStatus.BUYER_NO_SHOW,
                OrderStatus.NO_SHOW_CONTESTED,
                OrderStatus.COMPLETED,
                OrderStatus.EXPIRED,
                OrderStatus.REJECTED_BY_SELLER,
                OrderStatus.CANCELLED_BEFORE_CONFIRMATION,
                OrderStatus.CANCELLED_BY_BUYER_AFTER_CONFIRMATION,
                OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION,
                OrderStatus.CLOSED_WITH_ISSUE);
        assertThat(Arrays.asList(OrderAction.values())).contains(
                OrderAction.CONFIRM, OrderAction.REJECT, OrderAction.CANCEL,
                OrderAction.CONFIRM_RECEIVED, OrderAction.CONFIRM_COMPLETED);
        assertThat(Arrays.asList(OrderActorContext.values())).containsExactly(
                OrderActorContext.BUYER, OrderActorContext.MERCHANT,
                OrderActorContext.SYSTEM, OrderActorContext.ADMIN);
        assertThat(Arrays.asList(OrderEventType.values())).contains(
                OrderEventType.SELLER_CONFIRMED, OrderEventType.ORDER_CLOSED_WITH_ISSUE);
    }
}
