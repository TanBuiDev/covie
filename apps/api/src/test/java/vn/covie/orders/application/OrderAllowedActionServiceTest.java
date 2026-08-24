package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.security.OrderActorType;

class OrderAllowedActionServiceTest {
    private final OrderAllowedActionService service = new OrderAllowedActionService();
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @ParameterizedTest
    @MethodSource("cases")
    void returnsOnlyActionsAllowedForTheActor(OrderActionContext context, Set<OrderAction> expected) {
        assertThat(service.allowedActions(context)).containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                c(OrderActorType.BUYER, OrderType.PRODUCT, OrderStatus.PENDING_CONFIRMATION, ProductFulfillmentMethod.PICKUP, NOW.plusSeconds(1), null, Set.of(OrderAction.CANCEL)),
                c(OrderActorType.BUYER, OrderType.PRODUCT, OrderStatus.CONFIRMED, ProductFulfillmentMethod.SELLER_DELIVERY, null, null, Set.of(OrderAction.CONFIRM_RECEIVED, OrderAction.REQUEST_CANCELLATION)),
                c(OrderActorType.BUYER, OrderType.PRODUCT, OrderStatus.AWAITING_BUYER_CONFIRMATION, ProductFulfillmentMethod.SELLER_DELIVERY, null, null, Set.of(OrderAction.CONFIRM_RECEIVED, OrderAction.REPORT_DELIVERY_ISSUE)),
                c(OrderActorType.BUYER, OrderType.PRODUCT, OrderStatus.BUYER_NO_SHOW, ProductFulfillmentMethod.PICKUP, null, null, Set.of(OrderAction.CONTEST_NO_SHOW)),
                c(OrderActorType.BUYER, OrderType.SERVICE, OrderStatus.CONFIRMED, null, null, null, Set.of(OrderAction.CONFIRM_COMPLETED, OrderAction.REQUEST_CANCELLATION)),
                c(OrderActorType.MERCHANT, OrderType.PRODUCT, OrderStatus.PENDING_CONFIRMATION, ProductFulfillmentMethod.PICKUP, NOW.plusSeconds(1), null, Set.of(OrderAction.CONFIRM, OrderAction.REJECT)),
                c(OrderActorType.MERCHANT, OrderType.PRODUCT, OrderStatus.CONFIRMED, ProductFulfillmentMethod.SELLER_DELIVERY, null, null, Set.of(OrderAction.MARK_OUT_FOR_DELIVERY, OrderAction.MARK_DELIVERED, OrderAction.CANCEL)),
                c(OrderActorType.MERCHANT, OrderType.PRODUCT, OrderStatus.READY_FOR_PICKUP, ProductFulfillmentMethod.PICKUP, null, NOW.minusSeconds(1), Set.of(OrderAction.CANCEL, OrderAction.REPORT_BUYER_NO_SHOW)),
                c(OrderActorType.MERCHANT, OrderType.SERVICE, OrderStatus.CONFIRMED, null, null, null, Set.of(OrderAction.MARK_SERVICE_PERFORMED, OrderAction.CANCEL)),
                c(OrderActorType.ADMIN, OrderType.PRODUCT, OrderStatus.CONFIRMED, ProductFulfillmentMethod.PICKUP, null, null, Set.of()));
    }

    private static Arguments c(OrderActorType actor, OrderType type, OrderStatus status, ProductFulfillmentMethod method,
            Instant expiresAt, Instant noShowAt, Set<OrderAction> expected) {
        return Arguments.of(new OrderActionContext(actor, type, status, method, expiresAt, noShowAt, NOW), expected);
    }
}
