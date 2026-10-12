package vn.covie.ops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.covie.retention.TechnicalRetentionProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SchedulerEntrypointMetricsTest {
    @org.junit.jupiter.api.Test
    void outboxStopsAtConfiguredBatchAndAtEmptyQueue() {
        var registry = new SimpleMeterRegistry();
        try {
            var processor = mock(vn.covie.outbox.application.OutboxProcessor.class);
            when(processor.processOne()).thenReturn(true);
            var scheduler = new vn.covie.outbox.application.OutboxScheduler(processor, 3, new ScheduledJobMetrics(registry));
            scheduler.poll();
            verify(processor, times(3)).processOne();
            reset(processor);
            when(processor.processOne()).thenReturn(true, false);
            scheduler.poll();
            verify(processor, times(2)).processOne();
        } finally { registry.close(); }
    }

    @org.junit.jupiter.api.Test
    void disabledRetentionDoesNotRunOrCount() throws Exception {
        var registry = new SimpleMeterRegistry();
        try {
            var properties = mock(TechnicalRetentionProperties.class);
            var service = mock(vn.covie.retention.TechnicalRetentionService.class);
            var scheduler = new vn.covie.retention.TechnicalRetentionScheduler(properties, service, new ScheduledJobMetrics(registry));
            invoke(new Entry(scheduler.getClass(), "run", "retention", true), scheduler);
            verifyNoInteractions(service);
            assertThat(registry.get("covie.scheduler.runs").tag("job", "retention").counter().count()).isZero();
        } finally { registry.close(); }
    }

    record Entry(Class<?> type, String method, String job, boolean catchesFailure) {}
    static Stream<Entry> entries() {
        return Stream.of(
                new Entry(vn.covie.outbox.application.OutboxScheduler.class, "poll", "outbox", false),
                new Entry(vn.covie.needs.scheduling.NeedExpirationScheduler.class, "tick", "needs", false),
                new Entry(vn.covie.realtime.SseHeartbeatScheduler.class, "heartbeat", "sse-heartbeat", false),
                new Entry(vn.covie.retention.TechnicalRetentionScheduler.class, "run", "retention", true),
                new Entry(vn.covie.orders.application.OrderScheduledTransitionService.class, "expirePendingOrders", "orders", false),
                new Entry(vn.covie.orders.application.OrderScheduledTransitionService.class, "autoCompleteDueOrders", "orders", false),
                new Entry(vn.covie.orders.application.OrderScheduledTransitionService.class, "closeTimedOutIssues", "orders", false));
    }

    @ParameterizedTest @MethodSource("entries")
    void measuresSuccessfulEntrypoint(Entry entry) throws Exception {
        var registry = new SimpleMeterRegistry();
        try {
            Object scheduler = create(entry, new ScheduledJobMetrics(registry), null);
            invoke(entry, scheduler);
            assertThat(registry.get("covie.scheduler.runs").tag("job", entry.job()).counter().count()).isEqualTo(1);
            assertThat(registry.get("covie.scheduler.failures").tag("job", entry.job()).counter().count()).isZero();
            assertThat(registry.get("covie.scheduler.duration").tag("job", entry.job()).timer().count()).isEqualTo(1);
        } finally { registry.close(); }
    }

    @ParameterizedTest @MethodSource("entries")
    void measuresDependencyFailureAndPreservesHandling(Entry entry) throws Exception {
        var registry = new SimpleMeterRegistry();
        var failure = new IllegalStateException("synthetic dependency failure");
        try {
            Object scheduler = create(entry, new ScheduledJobMetrics(registry), failure);
            Throwable observed = null;
            try { invoke(entry, scheduler); } catch (InvocationTargetException ex) { observed = ex.getCause(); }
            assertThat(observed).isSameAs(entry.catchesFailure() ? null : failure);
            assertThat(registry.get("covie.scheduler.runs").tag("job", entry.job()).counter().count()).isEqualTo(1);
            assertThat(registry.get("covie.scheduler.failures").tag("job", entry.job()).counter().count()).isEqualTo(1);
            assertThat(registry.get("covie.scheduler.duration").tag("job", entry.job()).timer().count()).isEqualTo(1);
        } finally { registry.close(); }
    }

    private Object create(Entry entry, ScheduledJobMetrics metrics, RuntimeException failure) throws Exception {
        var constructor = Arrays.stream(entry.type().getConstructors()).max(java.util.Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
        Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = constructor.getParameterTypes()[i];
            if (type == ScheduledJobMetrics.class) args[i] = metrics;
            else if (type == int.class) args[i] = 50;
            else if (type == TechnicalRetentionProperties.class) {
                var properties = mock(TechnicalRetentionProperties.class); when(properties.enabled()).thenReturn(true); args[i] = properties;
            } else args[i] = failure == null ? mock(type) : mock(type, invocation -> { throw failure; });
        }
        return constructor.newInstance(args);
    }

    private void invoke(Entry entry, Object scheduler) throws Exception {
        var method = entry.type().getDeclaredMethod(entry.method());
        method.setAccessible(true);
        method.invoke(scheduler);
    }
}
