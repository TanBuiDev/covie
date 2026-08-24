package vn.covie.orders.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.api.dto.IssueRequest;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderIssueEntity;
import vn.covie.orders.model.OrderIssueType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderIssueRepository;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderIssueService {
    private final OrderTransitionService transitions;private final OrderAccessPolicy access;private final OrderIssueRepository issues;
    private final OrderPolicyProvider policy;private final OrderEventService events;private final OutboxEventWriter outbox;private final UuidV7Generator ids;private final vn.covie.shared.time.AppClock clock;
    public OrderIssueService(OrderTransitionService transitions,OrderAccessPolicy access,OrderIssueRepository issues,OrderPolicyProvider policy,OrderEventService events,OutboxEventWriter outbox,UuidV7Generator ids,vn.covie.shared.time.AppClock clock){this.transitions=transitions;this.access=access;this.issues=issues;this.policy=policy;this.events=events;this.outbox=outbox;this.ids=ids;this.clock=clock;}
    @Transactional public void reportDeliveryIssue(UUID orderId,IssueRequest request){report(orderId,request,OrderType.PRODUCT,OrderIssueType.DELIVERY_NOT_RECEIVED,OrderStatus.DELIVERY_ISSUE_REPORTED,OrderEventType.BUYER_REPORTED_NOT_RECEIVED);}
    @Transactional public void reportServiceIssue(UUID orderId,IssueRequest request){report(orderId,request,OrderType.SERVICE,OrderIssueType.SERVICE_NOT_COMPLETED,OrderStatus.SERVICE_ISSUE_REPORTED,OrderEventType.BUYER_REPORTED_SERVICE_NOT_COMPLETED);}
    private void report(UUID id,IssueRequest request,OrderType type,OrderIssueType issueType,OrderStatus target,OrderEventType eventType){
        OrderEntity order=transitions.lock(id).order();UUID actor=access.requireBuyer(order).actorUserId();if(order.getOrderType()!=type)throw denied();transitions.requireStatus(order,OrderStatus.AWAITING_BUYER_CONFIRMATION);
        if(request==null||request.reasonText()==null||request.reasonText().isBlank())throw denied();Instant now=clock.now();OrderPolicySnapshot p=policy.resolve(order.getMerchantProfileId(),order.getMerchantLocationId());
        issues.save(OrderIssueEntity.open(ids.generate(),id,issueType,actor,request.reasonText(),now,now.plus(p.issueCloseTimeout())));order.transitionTo(target,now);
        events.append(order,eventType,actor,OrderActorContext.BUYER,OrderStatus.AWAITING_BUYER_CONFIRMATION,target,null,request.reasonText(),Map.of());
        outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_ISSUE_REPORTED,"ORDER",id,Map.of("orderId",id,"orderCode",order.getOrderCode(),"status",target.name())));
    }
    private ApiException denied(){return new ApiException(ErrorCode.ORDER_ISSUE_NOT_ALLOWED,HttpStatus.CONFLICT,"Order issue is not allowed.");}
}
