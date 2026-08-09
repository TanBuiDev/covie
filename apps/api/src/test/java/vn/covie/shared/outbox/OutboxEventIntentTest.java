package vn.covie.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutboxEventIntentTest {
    @Test
    void copiesPayloadAtConstructionTime() {
        UUID orderId = UUID.randomUUID();
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        OutboxEventIntent intent = new OutboxEventIntent("ORDER_CONFIRMED", "ORDER", orderId, payload);

        payload.put("tampered", true);

        assertThat(intent.eventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(intent.payload()).containsEntry("orderId", orderId.toString());
        assertThat(intent.payload()).doesNotContainKey("tampered");
    }
}
