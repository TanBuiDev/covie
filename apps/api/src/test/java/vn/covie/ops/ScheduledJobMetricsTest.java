package vn.covie.ops;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ScheduledJobMetricsTest {
    @Test
    void countsEveryFixedJobAndMeasuresActualElapsedTime() {
        MockClock clock = new MockClock();
        var registry = new SimpleMeterRegistry(io.micrometer.core.instrument.simple.SimpleConfig.DEFAULT, clock);
        try {
            var metrics = new ScheduledJobMetrics(registry);
            var calls = new AtomicInteger();
            for (var job : ScheduledJobMetrics.Job.values()) {
                metrics.run(job, () -> { calls.incrementAndGet(); clock.add(Duration.ofMillis(250)); });
                assertThat(registry.get("covie.scheduler.runs").tag("job", job.tag()).counter().count()).isEqualTo(1);
                assertThat(registry.get("covie.scheduler.failures").tag("job", job.tag()).counter().count()).isZero();
                assertThat(registry.get("covie.scheduler.duration").tag("job", job.tag()).timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(250);
            }
            assertThat(calls.get()).isEqualTo(6);
            assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(1));
        } finally { registry.close(); }
    }

    @Test
    void countsFailureAndDurationWhilePreservingOriginalException() {
        MockClock clock = new MockClock();
        var registry = new SimpleMeterRegistry(io.micrometer.core.instrument.simple.SimpleConfig.DEFAULT, clock);
        try {
            var metrics = new ScheduledJobMetrics(registry);
            var failure = new IllegalStateException("fixture failure");
            assertThat(catchThrowable(() -> metrics.run(ScheduledJobMetrics.Job.ORDERS, () -> {
                clock.add(Duration.ofMillis(125)); throw failure;
            }))).isSameAs(failure);
            assertThat(registry.get("covie.scheduler.runs").tag("job", "orders").counter().count()).isEqualTo(1);
            assertThat(registry.get("covie.scheduler.failures").tag("job", "orders").counter().count()).isEqualTo(1);
            assertThat(registry.get("covie.scheduler.duration").tag("job", "orders").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(125);
        } finally { registry.close(); }
    }

    @Test
    void preservesErrorsToo() {
        var registry = new SimpleMeterRegistry();
        try {
            var failure = new AssertionError("fixture error");
            var metrics = new ScheduledJobMetrics(registry);
            assertThat(catchThrowable(() -> metrics.run(ScheduledJobMetrics.Job.OUTBOX, () -> { throw failure; }))).isSameAs(failure);
            assertThat(registry.get("covie.scheduler.failures").tag("job", "outbox").counter().count()).isEqualTo(1);
        } finally { registry.close(); }
    }
}
