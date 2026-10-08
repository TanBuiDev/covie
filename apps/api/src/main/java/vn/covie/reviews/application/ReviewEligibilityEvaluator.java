package vn.covie.reviews.application;

import org.springframework.stereotype.Component;
import vn.covie.orders.model.OrderStatus;
import vn.covie.reviews.api.dto.ReviewEligibilityReason;

@Component
public class ReviewEligibilityEvaluator {
    public ReviewEligibilityReason evaluate(boolean creationEnabled, OrderStatus orderStatus,
            boolean reviewableItem, boolean alreadyReviewed) {
        if (!creationEnabled) return ReviewEligibilityReason.CREATION_DISABLED;
        if (orderStatus != OrderStatus.COMPLETED) return ReviewEligibilityReason.ORDER_NOT_COMPLETED;
        if (!reviewableItem) return ReviewEligibilityReason.ITEM_NOT_REVIEWABLE;
        if (alreadyReviewed) return ReviewEligibilityReason.ALREADY_REVIEWED;
        return ReviewEligibilityReason.ELIGIBLE;
    }
}
