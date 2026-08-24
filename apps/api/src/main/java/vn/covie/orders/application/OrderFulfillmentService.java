package vn.covie.orders.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.model.ServiceFulfillmentEntity;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.persistence.ServiceFulfillmentRepository;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderFulfillmentService {
    private final OrderTransitionService transitions; private final OrderAccessPolicy access;
    private final ProductFulfillmentRepository products; private final ServiceFulfillmentRepository services;
    private final OrderPolicyProvider policy; private final OrderEventService events; private final OutboxEventWriter outbox;
    private final vn.covie.shared.time.AppClock clock;
    public OrderFulfillmentService(OrderTransitionService transitions, OrderAccessPolicy access,
            ProductFulfillmentRepository products, ServiceFulfillmentRepository services, OrderPolicyProvider policy,
            OrderEventService events, OutboxEventWriter outbox, vn.covie.shared.time.AppClock clock) {
        this.transitions=transitions;this.access=access;this.products=products;this.services=services;this.policy=policy;
        this.events=events;this.outbox=outbox;this.clock=clock;
    }
    @Transactional public void markOutForDelivery(UUID orderId) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        requireProduct(order); transitions.requireStatus(order,OrderStatus.CONFIRMED); ProductFulfillmentEntity fulfillment=product(orderId);
        if(fulfillment.getFulfillmentMethod()!=ProductFulfillmentMethod.SELLER_DELIVERY) throw conflict(); Instant now=clock.now();
        fulfillment.markOutForDelivery(now); order.transitionTo(OrderStatus.OUT_FOR_DELIVERY,now);
        event(order,OrderEventType.SELLER_MARKED_OUT_FOR_DELIVERY,actor,OrderStatus.CONFIRMED,now);
    }
    @Transactional public void markDelivered(UUID orderId) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        requireProduct(order); transitions.requireStatus(order,OrderStatus.CONFIRMED,OrderStatus.OUT_FOR_DELIVERY);
        ProductFulfillmentEntity fulfillment=product(orderId); if(fulfillment.getFulfillmentMethod()!=ProductFulfillmentMethod.SELLER_DELIVERY) throw conflict();
        Instant now=clock.now(); OrderStatus before=order.getStatus(); OrderPolicySnapshot p=policy.resolve(order.getMerchantProfileId(),order.getMerchantLocationId());
        fulfillment.markDelivered(now,now.plus(p.autoCompleteGrace())); order.transitionTo(OrderStatus.AWAITING_BUYER_CONFIRMATION,now);
        event(order,OrderEventType.SELLER_MARKED_DELIVERED,actor,before,now);
    }
    @Transactional public void markReadyForPickup(UUID orderId) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        requireProduct(order); transitions.requireStatus(order,OrderStatus.CONFIRMED); ProductFulfillmentEntity fulfillment=product(orderId);
        if(fulfillment.getFulfillmentMethod()!=ProductFulfillmentMethod.PICKUP) throw conflict(); Instant now=clock.now();
        OrderPolicySnapshot p=policy.resolve(order.getMerchantProfileId(),order.getMerchantLocationId());
        fulfillment.markReadyForPickup(now,now.plus(p.pickupNoShowGrace())); order.transitionTo(OrderStatus.READY_FOR_PICKUP,now);
        event(order,OrderEventType.SELLER_MARKED_READY_FOR_PICKUP,actor,OrderStatus.CONFIRMED,now);
    }
    @Transactional public void markServicePerformed(UUID orderId) {
        OrderEntity order=transitions.lock(orderId).order(); UUID actor=access.requireMerchantProcessor(order).actorUserId();
        if(order.getOrderType()!=OrderType.SERVICE) throw conflict(); transitions.requireStatus(order,OrderStatus.CONFIRMED);
        ServiceFulfillmentEntity fulfillment=services.findById(orderId).orElseThrow(this::notFound); Instant now=clock.now();
        OrderPolicySnapshot p=policy.resolve(order.getMerchantProfileId(),order.getMerchantLocationId()); fulfillment.markPerformed(now,now.plus(p.autoCompleteGrace()));
        order.transitionTo(OrderStatus.AWAITING_BUYER_CONFIRMATION,now); event(order,OrderEventType.PROVIDER_MARKED_PERFORMED,actor,OrderStatus.CONFIRMED,now);
    }
    private void event(OrderEntity order,OrderEventType type,UUID actor,OrderStatus before,Instant now) {
        events.append(order,type,actor,OrderActorContext.MERCHANT,before,order.getStatus(),null,null,Map.of());
        outbox.append(new OutboxEventIntent(OrderOutboxEvents.ORDER_FULFILLMENT_UPDATED,"ORDER",order.getId(),Map.of("orderId",order.getId(),"orderCode",order.getOrderCode(),"status",order.getStatus().name())));
    }
    private ProductFulfillmentEntity product(UUID id){return products.findById(id).orElseThrow(this::notFound);} private void requireProduct(OrderEntity order){if(order.getOrderType()!=OrderType.PRODUCT)throw conflict();}
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");} private ApiException conflict(){return new ApiException(ErrorCode.ORDER_STATE_CONFLICT,HttpStatus.CONFLICT,"The order can no longer perform this action.");}
}
