package vn.covie.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductFulfillmentLifecycleTest {
    @Test
    void recordsDeliveryProgressAndCompletionDeadline() {
        ProductFulfillmentEntity fulfillment = ProductFulfillmentEntity.create(UUID.randomUUID(),
                ProductFulfillmentMethod.SELLER_DELIVERY, UUID.randomUUID(), UUID.randomUUID(), 100,
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(), null);
        Instant now = Instant.parse("2026-08-22T01:00:00Z");

        fulfillment.markDelivered(now, now.plusSeconds(86400));

        assertThat(fulfillment.getSellerMarkedDeliveredAt()).isEqualTo(now);
        assertThat(fulfillment.getAwaitingBuyerConfirmationAt()).isEqualTo(now);
        assertThat(fulfillment.getAutoCompleteAt()).isEqualTo(now.plusSeconds(86400));
    }
}
