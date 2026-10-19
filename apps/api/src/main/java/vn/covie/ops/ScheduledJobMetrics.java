package vn.covie.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ScheduledJobMetrics {
    public enum Job {
        OUTBOX("outbox"), ORDERS("orders"), NEEDS("needs"), RETENTION("retention"), SSE_HEARTBEAT("sse-heartbeat"), CATALOG_VIDEO_CLEANUP("catalog-video-cleanup");
        private final String tag;
        Job(String tag) { this.tag = tag; }
        public String tag() { return tag; }
    }

    private record Meters(Counter runs, Counter failures, Timer duration) {}
    private final MeterRegistry registry;
    private final EnumMap<Job, Meters> meters = new EnumMap<>(Job.class);

    public ScheduledJobMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Job job : Job.values()) {
            meters.put(job, new Meters(
                    Counter.builder("covie.scheduler.runs").tag("job", job.tag()).register(registry),
                    Counter.builder("covie.scheduler.failures").tag("job", job.tag()).register(registry),
                    Timer.builder("covie.scheduler.duration").tag("job", job.tag()).register(registry)));
        }
    }

    public void run(Job job, Runnable operation) {
        Objects.requireNonNull(operation);
        Meters current = meters.get(Objects.requireNonNull(job));
        current.runs().increment();
        Timer.Sample sample = Timer.start(registry);
        try {
            operation.run();
        } catch (RuntimeException | Error failure) {
            current.failures().increment();
            throw failure;
        } finally {
            sample.stop(current.duration());
        }
    }
}
