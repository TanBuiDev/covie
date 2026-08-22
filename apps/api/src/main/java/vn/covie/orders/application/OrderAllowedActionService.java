package vn.covie.orders.application;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.covie.orders.model.OrderAction;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.model.ProductFulfillmentMethod;
import vn.covie.orders.security.OrderActorType;

@Service
public class OrderAllowedActionService {
    public Set<OrderAction> allowedActions(OrderActionContext context) {
        if (context.actorType() == OrderActorType.ADMIN || expiredPending(context)) return Set.of();
        return context.actorType() == OrderActorType.BUYER ? buyerActions(context) : merchantActions(context);
    }

    private static Set<OrderAction> buyerActions(OrderActionContext c) {
        if (c.status() == OrderStatus.PENDING_CONFIRMATION) return set(OrderAction.CANCEL);
        if (c.orderType() == OrderType.SERVICE) return switch (c.status()) {
            case CONFIRMED -> set(OrderAction.CONFIRM_COMPLETED, OrderAction.REQUEST_CANCELLATION);
            case AWAITING_BUYER_CONFIRMATION -> set(OrderAction.CONFIRM_COMPLETED, OrderAction.REPORT_SERVICE_ISSUE);
            case SERVICE_ISSUE_REPORTED -> set(OrderAction.CONFIRM_COMPLETED);
            default -> Set.of();
        };
        return switch (c.status()) {
            case CONFIRMED, OUT_FOR_DELIVERY -> set(OrderAction.CONFIRM_RECEIVED, OrderAction.REQUEST_CANCELLATION);
            case READY_FOR_PICKUP -> set(OrderAction.CONFIRM_RECEIVED, OrderAction.REQUEST_CANCELLATION);
            case AWAITING_BUYER_CONFIRMATION -> set(OrderAction.CONFIRM_RECEIVED, OrderAction.REPORT_DELIVERY_ISSUE);
            case DELIVERY_ISSUE_REPORTED, NO_SHOW_CONTESTED -> set(OrderAction.CONFIRM_RECEIVED);
            case BUYER_NO_SHOW -> set(OrderAction.CONTEST_NO_SHOW);
            default -> Set.of();
        };
    }

    private static Set<OrderAction> merchantActions(OrderActionContext c) {
        if (c.status() == OrderStatus.PENDING_CONFIRMATION) return set(OrderAction.CONFIRM, OrderAction.REJECT);
        if (c.status() == OrderStatus.CANCELLATION_REQUESTED) return set(OrderAction.ACCEPT_CANCELLATION, OrderAction.REJECT_CANCELLATION);
        if (c.orderType() == OrderType.SERVICE && c.status() == OrderStatus.CONFIRMED) return set(OrderAction.MARK_SERVICE_PERFORMED, OrderAction.CANCEL);
        if (c.orderType() != OrderType.PRODUCT) return Set.of();
        if (c.productFulfillmentMethod() == ProductFulfillmentMethod.SELLER_DELIVERY) return switch (c.status()) {
            case CONFIRMED -> set(OrderAction.MARK_OUT_FOR_DELIVERY, OrderAction.MARK_DELIVERED, OrderAction.CANCEL);
            case OUT_FOR_DELIVERY -> set(OrderAction.MARK_DELIVERED, OrderAction.CANCEL);
            default -> Set.of();
        };
        return switch (c.status()) {
            case CONFIRMED -> set(OrderAction.MARK_READY_FOR_PICKUP, OrderAction.CANCEL);
            case READY_FOR_PICKUP -> noShowReached(c) ? set(OrderAction.CANCEL, OrderAction.REPORT_BUYER_NO_SHOW) : set(OrderAction.CANCEL);
            default -> Set.of();
        };
    }

    private static boolean expiredPending(OrderActionContext c) {
        return c.status() == OrderStatus.PENDING_CONFIRMATION && (c.confirmationExpiresAt() == null || !c.now().isBefore(c.confirmationExpiresAt()));
    }
    private static boolean noShowReached(OrderActionContext c) {
        return c.noShowEligibleAt() != null && !c.now().isBefore(c.noShowEligibleAt());
    }
    private static Set<OrderAction> set(OrderAction first, OrderAction... rest) {
        return EnumSet.of(first, rest);
    }
}
