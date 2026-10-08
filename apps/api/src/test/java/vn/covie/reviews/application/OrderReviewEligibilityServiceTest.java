package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.reviews.api.dto.ReviewEligibilityReason;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.commerce.PaymentMethod;

class OrderReviewEligibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    @Test
    void evaluatesAllItemsWithOneReviewedIdQuery() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ReviewPolicyProvider policy = mock(ReviewPolicyProvider.class);
        when(policy.creationEnabled()).thenReturn(true);
        OrderEntity order = order(true);
        OrderItemEntity eligible = item(order, UUID.randomUUID());
        OrderItemEntity reviewed = item(order, UUID.randomUUID());
        OrderItemEntity notReviewable = item(order, null);
        List<UUID> itemIds = List.of(eligible.getId(), reviewed.getId(), notReviewable.getId());
        when(reviews.findReviewedOrderItemIds(itemIds)).thenReturn(List.of(reviewed.getId()));
        OrderReviewEligibilityService service = new OrderReviewEligibilityService(
                reviews, policy, new ReviewEligibilityEvaluator());

        var result = service.evaluate(order, List.of(eligible, reviewed, notReviewable));

        assertThat(result.get(eligible.getId()).eligible()).isTrue();
        assertThat(result.get(eligible.getId()).reason()).isEqualTo(ReviewEligibilityReason.ELIGIBLE);
        assertThat(result.get(eligible.getId()).orderItemId()).isEqualTo(eligible.getId());
        assertThat(result.get(reviewed.getId()).reason()).isEqualTo(ReviewEligibilityReason.ALREADY_REVIEWED);
        assertThat(result.get(notReviewable.getId()).reason()).isEqualTo(ReviewEligibilityReason.ITEM_NOT_REVIEWABLE);
        verify(reviews).findReviewedOrderItemIds(itemIds);
        verifyNoMoreInteractions(reviews);
    }

    @Test
    void appliesTheSharedReasonPrecedence() {
        ReviewEligibilityEvaluator evaluator = new ReviewEligibilityEvaluator();

        assertThat(evaluator.evaluate(false, OrderStatus.CONFIRMED, false, true))
                .isEqualTo(ReviewEligibilityReason.CREATION_DISABLED);
        assertThat(evaluator.evaluate(true, OrderStatus.CONFIRMED, false, true))
                .isEqualTo(ReviewEligibilityReason.ORDER_NOT_COMPLETED);
        assertThat(evaluator.evaluate(true, OrderStatus.COMPLETED, false, true))
                .isEqualTo(ReviewEligibilityReason.ITEM_NOT_REVIEWABLE);
        assertThat(evaluator.evaluate(true, OrderStatus.COMPLETED, true, true))
                .isEqualTo(ReviewEligibilityReason.ALREADY_REVIEWED);
        assertThat(evaluator.evaluate(true, OrderStatus.COMPLETED, true, false))
                .isEqualTo(ReviewEligibilityReason.ELIGIBLE);
    }

    private static OrderEntity order(boolean completed) {
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CV-REVIEW", OrderType.PRODUCT,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 100,
                OrderShippingFeeType.FREE, 0L, 100, 100L, "VND", NOW.plusSeconds(60), NOW);
        if (completed) order.complete(NOW);
        return order;
    }

    private static OrderItemEntity item(OrderEntity order, UUID catalogItemId) {
        return OrderItemEntity.create(UUID.randomUUID(), order.getId(), catalogItemId, UUID.randomUUID(),
                UUID.randomUUID(), CatalogItemType.PRODUCT, 1, 100, "VND", NullNode.getInstance(), NOW);
    }
}
