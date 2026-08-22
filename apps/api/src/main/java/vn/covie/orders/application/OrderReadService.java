package vn.covie.orders.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.orders.api.dto.OrderDetailResponse;
import vn.covie.orders.api.dto.OrderPageResponse;
import vn.covie.orders.api.dto.OrderSummaryResponse;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.orders.persistence.ProductFulfillmentRepository;
import vn.covie.orders.persistence.ServiceFulfillmentRepository;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.orders.api.dto.OrderTimelineEventResponse;
import vn.covie.orders.model.ProductFulfillmentEntity;
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderReadService {
    private final OrderRepository orders; private final OrderAccessPolicy access; private final OrderAllowedActionService actions;
    private final MerchantAccessPolicy merchantAccess; private final ProductFulfillmentRepository products; private final ServiceFulfillmentRepository services; private final OrderEventRepository events; private final vn.covie.shared.time.AppClock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public OrderReadService(OrderRepository orders, OrderAccessPolicy access, OrderAllowedActionService actions, MerchantAccessPolicy merchantAccess, ProductFulfillmentRepository products, ServiceFulfillmentRepository services, OrderEventRepository events, vn.covie.shared.time.AppClock clock) {
        this.orders=orders;this.access=access;this.actions=actions;this.merchantAccess=merchantAccess;this.products=products;this.services=services;this.events=events;this.clock=clock;
    }
    public OrderReadService(OrderRepository orders, OrderAccessPolicy access, OrderAllowedActionService actions, MerchantAccessPolicy merchantAccess, vn.covie.shared.time.AppClock clock) {
        this(orders,access,actions,merchantAccess,null,null,null,clock);
    }
    public OrderDetailResponse detail(UUID id) { OrderEntity order=orders.findById(id).orElseThrow(this::notFound); return map(order,access.requireVisible(order)); }
    public OrderPageResponse myOrders(UUID buyerId) {
        List<OrderSummaryResponse> items=orders.findAllByBuyerUserIdOrderByCreatedAtDesc(buyerId).stream()
                .map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();
        return new OrderPageResponse(items,null);
    }
    public OrderPageResponse merchantOrders(UUID merchantId) {
        merchantAccess.require(merchantId, MerchantCapability.VIEW_ORDERS);
        List<OrderSummaryResponse> items=orders.findAllByMerchantProfileIdOrderByCreatedAtDesc(merchantId).stream()
                .map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();
        return new OrderPageResponse(items,null);
    }
    public List<OrderTimelineEventResponse> events(UUID orderId) {
        OrderEntity order=orders.findById(orderId).orElseThrow(this::notFound); access.requireVisible(order);
        if(events==null) return List.of();
        return events.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream().map(event -> new OrderTimelineEventResponse(
                event.getId(),event.getEventType(),event.getActorUserId(),event.getActorContext(),event.getFromStatus(),
                event.getToStatus(),event.getReasonCode(),event.getNote(),event.getCreatedAt())).toList();
    }
    private OrderDetailResponse map(OrderEntity order,OrderAccess actor) {
        ProductFulfillmentEntity product=products==null?null:products.findById(order.getId()).orElse(null);
        Set<OrderAction> permitted=actions.allowedActions(new OrderActionContext(actor.actorType(),order.getOrderType(),order.getStatus(),product==null?null:product.getFulfillmentMethod(),order.getConfirmationExpiresAt(),product==null?null:product.getNoShowEligibleAt(),clock.now()));
        if(permitted==null) permitted=Set.of();
        return new OrderDetailResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getVersion(),
                order.getBuyerUserId(),order.getMerchantProfileId(),order.getMerchantLocationId(),permitted,order.getCreatedAt(),order.getConfirmedAt(),order.getCompletedAt(),order.getClosedAt());
    }
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}
}
