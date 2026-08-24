package vn.covie.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.shared.commerce.PaymentMethod;

class OrderLifecycleTransitionTest {
    @Test
    void confirmationSnapshotsTheConfirmedTimestampAndCurrentStatus() {
        Instant createdAt = Instant.parse("2026-08-22T00:00:00Z");
        OrderEntity order = OrderEntity.pending(UUID.randomUUID(), "CVTEST", OrderType.PRODUCT, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", createdAt.plusSeconds(1800), createdAt);

        order.confirm(Instant.parse("2026-08-22T00:01:00Z"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(Instant.parse("2026-08-22T00:01:00Z"));
    }
}
