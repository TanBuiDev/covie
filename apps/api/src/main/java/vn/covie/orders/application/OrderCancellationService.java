package vn.covie.orders.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.inventory.application.InventoryReservationService;
import vn.covie.orders.api.dto.CancellationRequestBody;
import vn.covie.orders.api.dto.CancellationResponseBody;
import vn.covie.orders.api.dto.CancelOrderRequest;
import vn.covie.orders.api.dto.OrderCommandResult;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderCancellationRequestEntity;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderCancellationRequestRepository;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderCancellationService {
    private final OrderTransitionService transitions; private final OrderAccessPolicy access;
    private final OrderCancellationRequestRepository requests; private final InventoryReservationService inventory;
    private final OrderEventService events; private final OutboxEventWriter outbox; private final UuidV7Generator ids;
    private final vn.covie.shared.time.AppClock clock;
    public OrderCancellationService(OrderTransitionService transitions, OrderAccessPolicy access,
            OrderCancellationRequestRepository requests, InventoryReservationService inventory, OrderEventService events,
            OutboxEventWriter outbox, UuidV7Generator ids, vn.covie.shared.time.AppClock clock) {
        this.transitions=transitions; this.access=access; this.requests=requests; this.inventory=inventory;
        this.events=events; this.outbox=outbox; this.ids=ids; this.clock=clock;
    }
    @Transactional
    public OrderCommandResult request(UUID orderId, CancellationRequestBody body) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireBuyer(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.CONFIRMED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.READY_FOR_PICKUP);
        if (body == null || body.reasonCode() == null || body.reasonCode().isBlank()) throw invalid();
        Instant now=clock.now(); OrderStatus previous=order.getStatus();
        requests.save(OrderCancellationRequestEntity.pending(ids.generate(), orderId, actor, body.reasonCode(), body.note(), previous, now));
        order.transitionTo(OrderStatus.CANCELLATION_REQUESTED, now);
        events.append(order, OrderEventType.BUYER_REQUESTED_CANCELLATION, actor, OrderActorContext.BUYER, previous,
                OrderStatus.CANCELLATION_REQUESTED, body.reasonCode(), body.note(), Map.of());
        outbox.append(intent(OrderOutboxEvents.ORDER_CANCELLATION_REQUESTED, order)); return result(order);
    }
    @Transactional
    public OrderCommandResult cancel(UUID orderId, CancelOrderRequest body) {
        OrderEntity order=transitions.lock(orderId).order(); var actorAccess=access.requireVisible(order);
        CancelOrderRequest normalized=body==null?new CancelOrderRequest(null,null):body; Instant now=clock.now();
        if(actorAccess.actorType()==vn.covie.orders.security.OrderActorType.BUYER){
            transitions.requireStatus(order,OrderStatus.PENDING_CONFIRMATION);
            if(order.getConfirmationExpiresAt()==null||!now.isBefore(order.getConfirmationExpiresAt()))throw conflict();
            inventory.releaseActiveForOrder(orderId,actorAccess.actorUserId(),reason(normalized,"BUYER_CANCELLED"),now);
            order.close(OrderStatus.CANCELLED_BEFORE_CONFIRMATION,now);
            events.append(order,OrderEventType.BUYER_CANCELLED_BEFORE_CONFIRMATION,actorAccess.actorUserId(),OrderActorContext.BUYER,OrderStatus.PENDING_CONFIRMATION,order.getStatus(),normalized.reasonCode(),normalized.note(),Map.of());
        }else{
            UUID actor=access.requireMerchantProcessor(order).actorUserId();
            transitions.requireStatus(order,OrderStatus.CONFIRMED,OrderStatus.OUT_FOR_DELIVERY,OrderStatus.READY_FOR_PICKUP);
            if(normalized.reasonCode()==null||normalized.reasonCode().isBlank())throw invalid();
            OrderStatus previous=order.getStatus(); inventory.releaseActiveForOrder(orderId,actor,normalized.reasonCode(),now);
            order.close(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION,now);
            events.append(order,OrderEventType.SELLER_CANCELLED_AFTER_CONFIRMATION,actor,OrderActorContext.MERCHANT,previous,order.getStatus(),normalized.reasonCode(),normalized.note(),Map.of());
        }
        outbox.append(intent(OrderOutboxEvents.ORDER_CANCELLED,order)); return result(order);
    }
    @Transactional
    public OrderCommandResult accept(UUID orderId, CancellationResponseBody body) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.CANCELLATION_REQUESTED); Instant now=clock.now();
        OrderCancellationRequestEntity request=requests.lockPendingByOrderId(orderId).orElseThrow(this::notPending);
        request.accept(actor, body == null ? null : body.note(), now); inventory.releaseActiveForOrder(orderId, actor, "CANCELLATION_ACCEPTED", now);
        order.close(OrderStatus.CANCELLED_BY_BUYER_AFTER_CONFIRMATION, now);
        events.append(order, OrderEventType.SELLER_ACCEPTED_CANCELLATION, actor, OrderActorContext.MERCHANT,
                OrderStatus.CANCELLATION_REQUESTED, order.getStatus(), null, body == null ? null : body.note(), Map.of());
        outbox.append(intent(OrderOutboxEvents.ORDER_CANCELLATION_RESPONDED, order)); return result(order);
    }
    @Transactional
    public OrderCommandResult reject(UUID orderId, CancellationResponseBody body) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        transitions.requireStatus(order, OrderStatus.CANCELLATION_REQUESTED); Instant now=clock.now();
        OrderCancellationRequestEntity request=requests.lockPendingByOrderId(orderId).orElseThrow(this::notPending);
        request.reject(actor, body == null ? null : body.note(), now); order.transitionTo(request.getPreviousOrderStatus(), now);
        events.append(order, OrderEventType.SELLER_REJECTED_CANCELLATION, actor, OrderActorContext.MERCHANT,
                OrderStatus.CANCELLATION_REQUESTED, order.getStatus(), null, body == null ? null : body.note(), Map.of());
        outbox.append(intent(OrderOutboxEvents.ORDER_CANCELLATION_RESPONDED, order)); return result(order);
    }
    private OutboxEventIntent intent(String type, OrderEntity order) { return new OutboxEventIntent(type,"ORDER",order.getId(),Map.of("orderId",order.getId(),"orderCode",order.getOrderCode(),"status",order.getStatus().name())); }
    private OrderCommandResult result(OrderEntity order) { return new OrderCommandResult(order.getId(),order.getOrderCode(),order.getStatus(),order.getVersion(),order.getUpdatedAt(),order.getConfirmedAt(),order.getCompletedAt(),order.getClosedAt(),Set.of()); }
    private ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY,"reasonCode is required."); }
    private ApiException conflict() { return new ApiException(ErrorCode.ORDER_STATE_CONFLICT,HttpStatus.CONFLICT,"The order can no longer perform this action."); }
    private static String reason(CancelOrderRequest request,String fallback){return request.reasonCode()==null||request.reasonCode().isBlank()?fallback:request.reasonCode();}
    private ApiException notPending() { return new ApiException(ErrorCode.ORDER_CANCELLATION_NOT_PENDING,HttpStatus.CONFLICT,"Cancellation request is not pending."); }
}
