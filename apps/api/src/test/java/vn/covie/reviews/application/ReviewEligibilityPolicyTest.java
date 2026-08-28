package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.security.OrderActorType;
import vn.covie.shared.commerce.PaymentMethod;

class ReviewEligibilityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void exposesEligibilityOnlyForAnUnreviewedCompletedBuyerOrderItem() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.pending(orderId, "CV-REVIEW", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 100, OrderShippingFeeType.FREE, 0L,
                100, 100L, "VND", NOW.plusSeconds(60), NOW);
        OrderItemEntity item = OrderItemEntity.create(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CatalogItemType.PRODUCT, 1, 100, "VND", NullNode.getInstance(), NOW);

        assertThat(ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.BUYER, false, true).eligible()).isFalse();
        assertThat(ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.BUYER, false, true).reason())
                .isEqualTo("ORDER_NOT_COMPLETED");

        order.complete(NOW.plusSeconds(1));
        assertThat(ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.MERCHANT, false, true).eligible()).isFalse();
        assertThat(ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.BUYER, true, true).reason())
                .isEqualTo("ALREADY_REVIEWED");
        assertThat(ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.BUYER, false, true).eligible()).isTrue();
    }
}
