package vn.covie.orders.application;

import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;

@Service
public class OrderScheduledTransitionService {
    private final OrderRepository orders;private final OrderTransitionService transitions;private final InventoryReservationService inventory;private final OrderEventService events;private final OutboxEventWriter outbox;private final vn.covie.shared.time.AppClock clock;
    public OrderScheduledTransitionService(OrderRepository orders,OrderTransitionService transitions,InventoryReservationService inventory,OrderEventService events,OutboxEventWriter outbox,vn.covie.shared.time.AppClock clock){this.orders=orders;this.transitions=transitions;this.inventory=inventory;this.events=events;this.outbox=outbox;this.clock=clock;}
    @Scheduled(fixedDelayString="${covie.orders.scheduler-delay:PT1M}") public void expirePendingOrders(){Instant now=clock.now();List<java.util.UUID> ids=orders.findTop100ByStatusAndConfirmationExpiresAtLessThanEqualOrderByConfirmationExpiresAtAsc(OrderStatus.PENDING_CONFIRMATION,now).stream().map(OrderEntity::getId).toList();for(java.util.UUID id:ids)expireOne(id);}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void expireOne(java.util.UUID orderId){OrderEntity order=transitions.lock(orderId).order();Instant now=clock.now();if(order.getStatus()!=OrderStatus.PENDING_CONFIRMATION||order.getConfirmationExpiresAt()==null||now.isBefore(order.getConfirmationExpiresAt()))return;inventory.releaseActiveForOrder(orderId,null,"ORDER_EXPIRED",now);order.close(OrderStatus.EXPIRED,now);events.append(order,OrderEventType.ORDER_EXPIRED,null,OrderActorContext.SYSTEM,OrderStatus.PENDING_CONFIRMATION,OrderStatus.EXPIRED,null,null,java.util.Map.of());outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_EXPIRED,"ORDER",orderId,java.util.Map.of("orderId",orderId,"orderCode",order.getOrderCode(),"status",order.getStatus().name())));}
}
