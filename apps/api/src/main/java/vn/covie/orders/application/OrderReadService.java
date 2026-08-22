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
import vn.covie.orders.security.OrderAccess;
import vn.covie.orders.security.OrderAccessPolicy;
import vn.covie.orders.security.OrderActorType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderReadService {
    private final OrderRepository orders; private final OrderAccessPolicy access; private final OrderAllowedActionService actions;
    private final vn.covie.shared.time.AppClock clock;
    public OrderReadService(OrderRepository orders, OrderAccessPolicy access, OrderAllowedActionService actions, vn.covie.shared.time.AppClock clock) {
        this.orders=orders;this.access=access;this.actions=actions;this.clock=clock;
    }
    public OrderDetailResponse detail(UUID id) { OrderEntity order=orders.findById(id).orElseThrow(this::notFound); return map(order,access.requireVisible(order)); }
    public OrderPageResponse myOrders(UUID buyerId) {
        List<OrderSummaryResponse> items=orders.findAllByBuyerUserIdOrderByCreatedAtDesc(buyerId).stream()
                .map(order->new OrderSummaryResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getCreatedAt())).toList();
        return new OrderPageResponse(items,null);
    }
    private OrderDetailResponse map(OrderEntity order,OrderAccess actor) {
        Set<OrderAction> permitted=actions.allowedActions(new OrderActionContext(actor.actorType(),order.getOrderType(),order.getStatus(),null,order.getConfirmationExpiresAt(),null,clock.now()));
        if(permitted==null) permitted=Set.of();
        return new OrderDetailResponse(order.getId(),order.getOrderCode(),order.getOrderType(),order.getStatus(),order.getVersion(),
                order.getBuyerUserId(),order.getMerchantProfileId(),order.getMerchantLocationId(),permitted,order.getCreatedAt(),order.getConfirmedAt(),order.getCompletedAt(),order.getClosedAt());
    }
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}
}
