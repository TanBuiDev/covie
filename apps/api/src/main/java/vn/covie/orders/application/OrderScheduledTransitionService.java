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
import vn.covie.orders.persistence.OrderIssueRepository;
import vn.covie.orders.model.OrderIssueStatus;
import org.springframework.data.domain.PageRequest;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;

@Service
public class OrderScheduledTransitionService {
    private final OrderRepository orders;private final OrderTransitionService transitions;private final InventoryReservationService inventory;private final OrderEventService events;private final OutboxEventWriter outbox;private final OrderCompletionService completion;private final OrderIssueRepository issues;private final vn.covie.shared.time.AppClock clock;
    public OrderScheduledTransitionService(OrderRepository orders,OrderTransitionService transitions,InventoryReservationService inventory,OrderEventService events,OutboxEventWriter outbox,OrderCompletionService completion,OrderIssueRepository issues,vn.covie.shared.time.AppClock clock){this.orders=orders;this.transitions=transitions;this.inventory=inventory;this.events=events;this.outbox=outbox;this.completion=completion;this.issues=issues;this.clock=clock;}
    @Scheduled(fixedDelayString="${covie.orders.scheduler-delay:PT1M}") public void expirePendingOrders(){Instant now=clock.now();List<java.util.UUID> ids=orders.findTop100ByStatusAndConfirmationExpiresAtLessThanEqualOrderByConfirmationExpiresAtAsc(OrderStatus.PENDING_CONFIRMATION,now).stream().map(OrderEntity::getId).toList();for(java.util.UUID id:ids)expireOne(id);}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void expireOne(java.util.UUID orderId){OrderEntity order=transitions.lock(orderId).order();Instant now=clock.now();if(order.getStatus()!=OrderStatus.PENDING_CONFIRMATION||order.getConfirmationExpiresAt()==null||now.isBefore(order.getConfirmationExpiresAt()))return;inventory.releaseActiveForOrder(orderId,null,"ORDER_EXPIRED",now);order.close(OrderStatus.EXPIRED,now);events.append(order,OrderEventType.ORDER_EXPIRED,null,OrderActorContext.SYSTEM,OrderStatus.PENDING_CONFIRMATION,OrderStatus.EXPIRED,null,null,java.util.Map.of());outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_EXPIRED,"ORDER",orderId,java.util.Map.of("orderId",orderId,"orderCode",order.getOrderCode(),"status",order.getStatus().name())));}
    @Scheduled(fixedDelayString="${covie.orders.scheduler-delay:PT1M}") public void autoCompleteDueOrders(){Instant now=clock.now();var page=PageRequest.of(0,100);var ids=new java.util.ArrayList<java.util.UUID>();ids.addAll(orders.findDueProductAutoCompletionIds(OrderStatus.AWAITING_BUYER_CONFIRMATION,now,page));ids.addAll(orders.findDueServiceAutoCompletionIds(OrderStatus.AWAITING_BUYER_CONFIRMATION,now,page));for(var id:ids)autoCompleteOne(id);}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void autoCompleteOne(java.util.UUID orderId){OrderEntity order=transitions.lock(orderId).order();if(order.getStatus()==OrderStatus.AWAITING_BUYER_CONFIRMATION)completion.autoComplete(order);}
    @Scheduled(fixedDelayString="${covie.orders.scheduler-delay:PT1M}") public void closeTimedOutIssues(){Instant now=clock.now();for(var issue:issues.findTop100ByIssueStatusAndCloseAfterAtLessThanEqualOrderByCloseAfterAtAsc(OrderIssueStatus.OPEN,now))closeIssueOne(issue.getOrderId());}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void closeIssueOne(java.util.UUID orderId){OrderEntity order=transitions.lock(orderId).order();var issue=issues.lockOpenByOrderId(orderId).orElse(null);Instant now=clock.now();if(issue==null||issue.getCloseAfterAt().isAfter(now))return;if(order.getStatus()==OrderStatus.DELIVERY_ISSUE_REPORTED||order.getStatus()==OrderStatus.SERVICE_ISSUE_REPORTED){OrderStatus before=order.getStatus();issue.closeByTimeout(now);order.close(OrderStatus.CLOSED_WITH_ISSUE,now);events.append(order,OrderEventType.ORDER_CLOSED_WITH_ISSUE,null,OrderActorContext.SYSTEM,before,OrderStatus.CLOSED_WITH_ISSUE,null,null,java.util.Map.of());outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_CLOSED_WITH_ISSUE,"ORDER",orderId,java.util.Map.of("orderId",orderId,"orderCode",order.getOrderCode(),"status",order.getStatus().name())));}}
}
