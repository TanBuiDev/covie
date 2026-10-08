package vn.covie.reviews.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.reviews.api.dto.ReviewEligibilityReason;
import vn.covie.reviews.api.dto.ReviewEligibilityResponse;
import vn.covie.reviews.persistence.ReviewRepository;

@Service
public class OrderReviewEligibilityService {
    private final ReviewRepository reviews;
    private final ReviewPolicyProvider policy;
    private final ReviewEligibilityEvaluator evaluator;

    public OrderReviewEligibilityService(ReviewRepository reviews, ReviewPolicyProvider policy,
            ReviewEligibilityEvaluator evaluator) {
        this.reviews = reviews;
        this.policy = policy;
        this.evaluator = evaluator;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ReviewEligibilityResponse> evaluate(OrderEntity order, List<OrderItemEntity> items) {
        if (items.isEmpty()) return Map.of();
        List<UUID> itemIds = items.stream().map(OrderItemEntity::getId).toList();
        Set<UUID> reviewedIds = Set.copyOf(reviews.findReviewedOrderItemIds(itemIds));
        boolean creationEnabled = policy.creationEnabled();
        Map<UUID, ReviewEligibilityResponse> result = new LinkedHashMap<>();
        for (OrderItemEntity item : items) {
            ReviewEligibilityReason reason = evaluator.evaluate(creationEnabled, order.getStatus(),
                    item.getCatalogItemId() != null, reviewedIds.contains(item.getId()));
            result.put(item.getId(), new ReviewEligibilityResponse(
                    item.getId(), reason == ReviewEligibilityReason.ELIGIBLE, reason));
        }
        return Map.copyOf(result);
    }
}
