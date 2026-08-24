package vn.covie.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import vn.covie.ratelimit.RateLimitGroup;

@Component
public final class OperationalMetrics {
    private final MeterRegistry registry;
    private final AtomicLong retentionLastSuccess = new AtomicLong(0);

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("covie.retention.last.success.unixtime", retentionLastSuccess, AtomicLong::get)
                .description("Unix timestamp of the most recent successful retention sweep")
                .register(registry);
    }

    public void rateLimitDenied(RateLimitGroup group) {
        counter("covie.ratelimit.requests", "group", group.name(), "outcome", "denied").increment();
    }

    public void rateLimitDegraded(RateLimitGroup group) {
        counter("covie.ratelimit.requests", "group", group.name(), "outcome", "degraded_fail_open").increment();
    }

    public void retentionDeleted(String dataset, int rows) {
        if (rows > 0) counter("covie.retention.deleted", "dataset", dataset).increment(rows);
    }

    public void retentionFailure() {
        counter("covie.retention.runs", "outcome", "failed").increment();
    }

    public void retentionSuccess() {
        counter("covie.retention.runs", "outcome", "success").increment();
        retentionLastSuccess.set(Instant.now().getEpochSecond());
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
