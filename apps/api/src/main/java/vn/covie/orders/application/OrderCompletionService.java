package vn.covie.orders.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderCompletionService {
    private final OrderTransitionService transitions; private final OrderAccessPolicy access;
    private final InventoryReservationService inventory; private final OrderEventService events;
    private final OutboxEventWriter outbox; private final vn.covie.shared.time.AppClock clock;
    public OrderCompletionService(OrderTransitionService transitions, OrderAccessPolicy access, InventoryReservationService inventory,
            OrderEventService events, OutboxEventWriter outbox, vn.covie.shared.time.AppClock clock) {
        this.transitions=transitions;this.access=access;this.inventory=inventory;this.events=events;this.outbox=outbox;this.clock=clock;
    }
    @Transactional public void confirmReceived(UUID orderId) { completeAsBuyer(orderId,OrderEventType.BUYER_CONFIRMED_RECEIVED); }
    @Transactional public void confirmCompleted(UUID orderId) { completeAsBuyer(orderId,OrderEventType.BUYER_CONFIRMED_SERVICE_COMPLETED); }
    @Transactional public void autoComplete(OrderEntity order) { complete(order,null,OrderActorContext.SYSTEM,OrderEventType.ORDER_AUTO_COMPLETED); }
    private void completeAsBuyer(UUID orderId,OrderEventType eventType) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireBuyer(order).actorUserId();
        if(order.getOrderType()==OrderType.PRODUCT) transitions.requireStatus(order,OrderStatus.CONFIRMED,OrderStatus.OUT_FOR_DELIVERY,OrderStatus.READY_FOR_PICKUP,OrderStatus.AWAITING_BUYER_CONFIRMATION,OrderStatus.DELIVERY_ISSUE_REPORTED,OrderStatus.NO_SHOW_CONTESTED);
        else transitions.requireStatus(order,OrderStatus.CONFIRMED,OrderStatus.AWAITING_BUYER_CONFIRMATION,OrderStatus.SERVICE_ISSUE_REPORTED);
        complete(order,actor,OrderActorContext.BUYER,eventType);
    }
    private void complete(OrderEntity order,UUID actor,OrderActorContext context,OrderEventType eventType) {
        Instant now=clock.now(); OrderStatus before=order.getStatus(); if(order.getOrderType()==OrderType.PRODUCT) inventory.commitActiveForOrder(order.getId(),actor,now);
        order.complete(now); events.append(order,eventType,actor,context,before,OrderStatus.COMPLETED,null,null,Map.of());
        outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_COMPLETED,"ORDER",order.getId(),Map.of("orderId",order.getId(),"orderCode",order.getOrderCode(),"status",order.getStatus().name())));
    }
    private ApiException conflict(){return new ApiException(ErrorCode.ORDER_STATE_CONFLICT,HttpStatus.CONFLICT,"The order can no longer perform this action.");}
}
