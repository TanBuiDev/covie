package vn.covie.ops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.realtime.SseConnectionRegistry;
import static org.assertj.core.api.Assertions.assertThat;

class SseOperationalMetricsTest {
    @Test
    void countsConnectionsRatherThanUsersWithoutUserTags() {
        var meters = new SimpleMeterRegistry();
        try {
            var connections = new SseConnectionRegistry(1000);
            new SseOperationalMetrics(meters, connections);
            var gauge = meters.get("covie.sse.connections").gauge();
            assertThat(gauge.value()).isZero();
            var user = UUID.randomUUID();
            connections.register(user);
            connections.register(user);
            connections.register(UUID.randomUUID());
            assertThat(connections.connectionCount()).isEqualTo(3);
            assertThat(gauge.value()).isEqualTo(3);
            assertThat(gauge.getId().getTags()).isEmpty();
        } finally { meters.close(); }
    }
}
