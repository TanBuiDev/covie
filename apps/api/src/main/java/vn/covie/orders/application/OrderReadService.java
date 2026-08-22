package vn.covie.orders.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderCancellationRequestRepository;
import vn.covie.orders.persistence.OrderIssueRepository;
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
    private final MerchantAccessPolicy merchantAccess; private final ProductFulfillmentRepository products; private final ServiceFulfillmentRepository services; private final OrderEventRepository events; private final OrderItemRepository items; private final OrderCancellationRequestRepository cancellations; private final OrderIssueRepository issues; private final vn.covie.shared.time.AppClock clock;
    private final OrderCursorCodec cursors=new OrderCursorCodec();
    @org.springframework.beans.factory.annotation.Autowired
    public OrderReadService(OrderRepository orders, OrderAccessPolicy access, OrderAllowedActionService actions, MerchantAccessPolicy merchantAccess, ProductFulfillmentRepository products, ServiceFulfillmentRepository services, OrderEventRepository events, OrderItemRepository items, OrderCancellationRequestRepository cancellations, OrderIssueRepository issues, vn.covie.shared.time.AppClock clock) {
        this.orders=orders;this.access=access;this.actions=actions;this.merchantAccess=merchantAccess;this.products=products;this.services=services;this.events=events;this.items=items;this.cancellations=cancellations;this.issues=issues;this.clock=clock;
    }
    public OrderReadService(OrderRepository orders, OrderAccessPolicy access, OrderAllowedActionService actions, MerchantAccessPolicy merchantAccess, vn.covie.shared.time.AppClock clock) {
        this(orders,access,actions,merchantAccess,null,null,null,null,null,null,clock);
    }
    public OrderDetailResponse detail(UUID id) { OrderEntity order=orders.findById(id).orElseThrow(this::notFound); return map(order,access.requireVisible(order)); }
    public OrderPageResponse myOrders(UUID buyerId) {
        List<OrderSummaryResponse> items=orders.findAllByBuyerUserIdOrderByCreatedAtDesc(buyerId).stream()
                .map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();
        return new OrderPageResponse(items,null);
    }
    public OrderPageResponse myOrders(UUID buyerId,int limit,String cursor) { var after=cursor(cursor); return page(orders.findBuyerPage(buyerId,after==null?null:after.createdAt(),after==null?null:after.id(),PageRequest.of(0,limit+1)),limit); }
    public OrderPageResponse merchantOrders(UUID merchantId) {
        merchantAccess.require(merchantId, MerchantCapability.VIEW_ORDERS);
        List<OrderSummaryResponse> items=orders.findAllByMerchantProfileIdOrderByCreatedAtDesc(merchantId).stream()
                .map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();
        return new OrderPageResponse(items,null);
    }
    public OrderPageResponse merchantOrders(UUID merchantId,UUID locationId,int limit,String cursor) {
        var after=cursor(cursor);
        var allowed=merchantAccess.readableOrderLocations(merchantId); if(!allowed.isEmpty()&&(locationId==null||!allowed.contains(locationId))) throw notFound();
        if(!allowed.isEmpty()) { var all=new java.util.ArrayList<OrderEntity>(); for(UUID id:allowed) all.addAll(orders.findMerchantPage(merchantId,id,after==null?null:after.createdAt(),after==null?null:after.id(),PageRequest.of(0,limit+1))); all.sort(java.util.Comparator.comparing(OrderEntity::getCreatedAt).reversed().thenComparing(OrderEntity::getId,java.util.Comparator.reverseOrder())); return page(all,limit); }
        return page(orders.findMerchantPage(merchantId,locationId,after==null?null:after.createdAt(),after==null?null:after.id(),PageRequest.of(0,limit+1)),limit);
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
        var service=services==null?null:services.findById(order.getId()).orElse(null);
        return new OrderDetailResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getVersion(),
                order.getSourceType(),new vn.covie.orders.api.dto.OrderPartyResponse(order.getBuyerUserId(),null,null),
                new vn.covie.orders.api.dto.OrderPartyResponse(null,order.getMerchantProfileId(),order.getMerchantLocationId()),
                items==null?List.of():items.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream().map(item->new vn.covie.orders.api.dto.OrderItemResponse(item.getId(),item.getCatalogItemId(),item.getVariantId(),item.getLocationVariantId(),item.getItemType(),item.getQuantity(),item.getUnitPrice(),item.getLineTotal(),item.getCurrencyCode(),item.getItemSnapshot())).toList(),
                new vn.covie.orders.api.dto.OrderPricingResponse(order.getSubtotal(),order.getShippingFeeType(),order.getShippingFee(),order.getKnownTotal(),order.getTotalAmount(),order.getCurrencyCode()),
                fulfillment(product,service,actor),order.getPaymentMethod(),cancellation(order.getId()),issue(order.getId()),events==null?List.of():events(order.getId()),permitted,order.getCreatedAt(),order.getConfirmedAt(),order.getCompletedAt(),order.getClosedAt());
    }
    private vn.covie.orders.api.dto.OrderFulfillmentResponse fulfillment(ProductFulfillmentEntity product,vn.covie.orders.model.ServiceFulfillmentEntity service,OrderAccess actor){
        boolean sensitive=actor.actorType()==OrderActorType.BUYER||actor.actorType()==OrderActorType.MERCHANT||actor.actorType()==OrderActorType.ADMIN;
        if(product!=null)return new vn.covie.orders.api.dto.OrderFulfillmentResponse(product.getFulfillmentMethod(),product.getPickupLocationId(),product.getBuyerAddressId(),product.getDistanceMeters(),product.getPickupLocationSnapshot(),sensitive?product.getDeliveryAddressSnapshot():null,sensitive?product.getBuyerContactSnapshot():null,product.getMerchantContactSnapshot(),product.getSellerMarkedReadyAt(),product.getOutForDeliveryAt(),product.getSellerMarkedDeliveredAt(),null,product.getAwaitingBuyerConfirmationAt(),product.getAutoCompleteAt(),product.getNoShowEligibleAt());
        if(service!=null)return new vn.covie.orders.api.dto.OrderFulfillmentResponse(null,null,service.getServiceAddressId(),null,null,sensitive?service.getServiceAddressSnapshot():null,sensitive?service.getBuyerContactSnapshot():null,service.getMerchantContactSnapshot(),null,null,null,service.getProviderMarkedPerformedAt(),service.getAwaitingBuyerConfirmationAt(),service.getAutoCompleteAt(),null);
        return null;
    }
    private vn.covie.orders.api.dto.OrderCancellationResponse cancellation(UUID orderId){if(cancellations==null)return null;return cancellations.findFirstByOrderIdOrderByCreatedAtDesc(orderId).map(c->new vn.covie.orders.api.dto.OrderCancellationResponse(c.getId(),c.getRequestedByUserId(),c.getReasonCode(),c.getReasonText(),c.getRequestStatus(),c.getPreviousOrderStatus(),c.getRespondedByUserId(),c.getResponseNote(),c.getCreatedAt(),c.getRespondedAt())).orElse(null);}
    private vn.covie.orders.api.dto.OrderIssueResponse issue(UUID orderId){if(issues==null)return null;return issues.findFirstByOrderIdOrderByOpenedAtDesc(orderId).map(i->new vn.covie.orders.api.dto.OrderIssueResponse(i.getId(),i.getIssueType(),i.getReportedByUserId(),i.getReasonText(),i.getIssueStatus(),i.getOpenedAt(),i.getCloseAfterAt(),i.getResolvedAt(),i.getClosedAt())).orElse(null);}
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}
    private OrderCursorCodec.Cursor cursor(String value){return cursors.decode(value);}
    private OrderPageResponse page(List<OrderEntity> rows,int limit){boolean hasMore=rows.size()>limit;List<OrderEntity> visible=hasMore?rows.subList(0,limit):rows;List<OrderSummaryResponse> items=visible.stream().map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();String next=hasMore?cursors.encode(new OrderCursorCodec.Cursor(visible.getLast().getCreatedAt(),visible.getLast().getId())):null;return new OrderPageResponse(items,next);}
}
