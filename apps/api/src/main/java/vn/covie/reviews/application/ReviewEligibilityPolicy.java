package vn.covie.reviews.application;

import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.security.OrderActorType;
import vn.covie.reviews.api.dto.ReviewEligibilityResponse;

public final class ReviewEligibilityPolicy {
    private ReviewEligibilityPolicy() {}

    public static ReviewEligibilityResponse evaluate(OrderEntity order, OrderItemEntity item, OrderActorType actorType,
            boolean alreadyReviewed, boolean creationEnabled) {
        if (actorType != OrderActorType.BUYER) return denied("NOT_BUYER");
        if (!creationEnabled) return denied("CREATION_DISABLED");
        if (order.getStatus() != OrderStatus.COMPLETED) return denied("ORDER_NOT_COMPLETED");
        if (item.getCatalogItemId() == null) return denied("ITEM_NOT_REVIEWABLE");
        if (alreadyReviewed) return denied("ALREADY_REVIEWED");
        return new ReviewEligibilityResponse(true, "ELIGIBLE");
    }

    private static ReviewEligibilityResponse denied(String reason) {
        return new ReviewEligibilityResponse(false, reason);
    }
}
