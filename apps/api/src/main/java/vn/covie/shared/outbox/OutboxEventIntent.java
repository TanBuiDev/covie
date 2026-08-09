package vn.covie.shared.outbox;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboxEventIntent(
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload) {
    public OutboxEventIntent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }
}
