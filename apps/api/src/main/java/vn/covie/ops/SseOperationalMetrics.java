package vn.covie.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import vn.covie.realtime.SseConnectionRegistry;

@Component
public final class SseOperationalMetrics {
    public SseOperationalMetrics(MeterRegistry registry, SseConnectionRegistry connections) {
        Gauge.builder("covie.sse.connections", connections, SseConnectionRegistry::connectionCount)
                .description("Current SSE connections in this process")
                .register(registry);
    }
}
