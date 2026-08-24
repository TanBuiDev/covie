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
import vn.covie.orders.model.OrderIssueEntity;
import vn.covie.orders.model.OrderIssueType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.persistence.OrderIssueRepository;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service public class OrderNoShowService {
    private final OrderTransitionService transitions;private final OrderAccessPolicy access;private final ProductFulfillmentRepository fulfillments;private final OrderIssueRepository issues;private final InventoryReservationService inventory;private final OrderPolicyProvider policy;private final OrderEventService events;private final OutboxEventWriter outbox;private final UuidV7Generator ids;private final vn.covie.shared.time.AppClock clock;
    public OrderNoShowService(OrderTransitionService transitions,OrderAccessPolicy access,ProductFulfillmentRepository fulfillments,OrderIssueRepository issues,InventoryReservationService inventory,OrderPolicyProvider policy,OrderEventService events,OutboxEventWriter outbox,UuidV7Generator ids,vn.covie.shared.time.AppClock clock){this.transitions=transitions;this.access=access;this.fulfillments=fulfillments;this.issues=issues;this.inventory=inventory;this.policy=policy;this.events=events;this.outbox=outbox;this.ids=ids;this.clock=clock;}
    @Transactional public void reportBuyerNoShow(UUID orderId){
        OrderEntity order=transitions.lock(orderId).order();UUID actor=access.requireMerchantProcessor(order).actorUserId();transitions.requireStatus(order,OrderStatus.READY_FOR_PICKUP);ProductFulfillmentEntity f=fulfillments.findById(orderId).orElseThrow(this::notFound);if(order.getOrderType()!=OrderType.PRODUCT||f.getFulfillmentMethod()!=ProductFulfillmentMethod.PICKUP)throw conflict();Instant now=clock.now();if(f.getNoShowEligibleAt()==null||now.isBefore(f.getNoShowEligibleAt()))throw new ApiException(ErrorCode.ORDER_NO_SHOW_GRACE_NOT_REACHED,HttpStatus.CONFLICT,"No-show grace is not reached.",Map.of("eligibleAt",String.valueOf(f.getNoShowEligibleAt())));
        inventory.releaseActiveForOrder(orderId,actor,"BUYER_NO_SHOW",now);order.transitionTo(OrderStatus.BUYER_NO_SHOW,now);event(order,OrderEventType.SELLER_MARKED_BUYER_NO_SHOW,actor,OrderActorContext.MERCHANT,OrderStatus.READY_FOR_PICKUP,now,OrderOutboxEvents.ORDER_FULFILLMENT_UPDATED);
    }
    @Transactional public void contest(UUID orderId,String note){
        OrderEntity order=transitions.lock(orderId).order();UUID actor=access.requireBuyer(order).actorUserId();transitions.requireStatus(order,OrderStatus.BUYER_NO_SHOW);Instant now=clock.now();OrderPolicySnapshot p=policy.resolve(order.getMerchantProfileId(),order.getMerchantLocationId());issues.save(OrderIssueEntity.open(ids.generate(),orderId,OrderIssueType.BUYER_NO_SHOW_CONTESTED,actor,note,now,now.plus(p.issueCloseTimeout())));order.transitionTo(OrderStatus.NO_SHOW_CONTESTED,now);event(order,OrderEventType.BUYER_CONTESTED_NO_SHOW,actor,OrderActorContext.BUYER,OrderStatus.BUYER_NO_SHOW,now,OrderOutboxEvents.ORDER_ISSUE_REPORTED);
    }
    private void event(OrderEntity o,OrderEventType event,UUID actor,OrderActorContext context,OrderStatus before,Instant now,String outboxType){events.append(o,event,actor,context,before,o.getStatus(),null,null,Map.of());outbox.append(new OutboxEventIntent(outboxType,"ORDER",o.getId(),Map.of("orderId",o.getId(),"orderCode",o.getOrderCode(),"status",o.getStatus().name())));}
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}private ApiException conflict(){return new ApiException(ErrorCode.ORDER_STATE_CONFLICT,HttpStatus.CONFLICT,"The order can no longer perform this action.");}
}
