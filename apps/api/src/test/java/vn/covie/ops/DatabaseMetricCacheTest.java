package vn.covie.ops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DatabaseMetricCacheTest {
    @Test
    void unavailableDatabasePublishesNaNAndLaterRefreshRecovers() {
        var jdbc = mock(JdbcTemplate.class);
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new DatabaseOperationalMetrics(jdbc);
            metrics.bindTo(registry);
            when(jdbc.queryForObject(anyString(), eq(Long.class))).thenThrow(new IllegalStateException("unavailable"));
            when(jdbc.queryForObject(anyString(), eq(Double.class))).thenThrow(new IllegalStateException("unavailable"));
            metrics.refresh();
            registry.getMeters().forEach(m -> assertThat(((io.micrometer.core.instrument.Gauge)m).value()).isNaN());
            reset(jdbc);
            when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
            when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(0.0);
            metrics.refresh();
            clearInvocations(jdbc);
            registry.getMeters().forEach(m -> assertThat(((io.micrometer.core.instrument.Gauge)m).value()).isZero());
            verifyNoInteractions(jdbc);
        } finally { registry.close(); }
    }

    @Test
    void scrapesUseCacheAndRefreshUpdatesIt() {
        var jdbc = mock(JdbcTemplate.class);
        var registry = new SimpleMeterRegistry();
        try {
            when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
            when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(12.0);
            var metrics = new DatabaseOperationalMetrics(jdbc);
            metrics.bindTo(registry);
            clearInvocations(jdbc);
            registry.get("covie.outbox.backlog").gauge().value();
            registry.get("covie.outbox.backlog").gauge().value();
            verifyNoInteractions(jdbc);
            metrics.refresh();
            clearInvocations(jdbc);
            assertThat(registry.get("covie.outbox.backlog").gauge().value()).isEqualTo(7);
            assertThat(registry.get("covie.outbox.due").gauge().value()).isEqualTo(7);
            assertThat(registry.get("covie.outbox.oldest.due.seconds").gauge().value()).isEqualTo(12);
            verifyNoInteractions(jdbc);
        } finally { registry.close(); }
    }
}
