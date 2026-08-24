package vn.covie.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class DatabaseOperationalMetrics implements MeterBinder {
    private final JdbcTemplate jdbc;

    public DatabaseOperationalMetrics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("covie.outbox.backlog", this, ignored -> count("select count(*) from outbox_events where status in ('PENDING','FAILED')"))
                .description("Pending and failed outbox events")
                .register(registry);
        Gauge.builder("covie.checkout.expired.sessions", this, ignored -> count("select count(*) from checkout_sessions where consumed_at is null and expires_at < now()"))
                .description("Expired unconsumed checkout sessions awaiting retention")
                .register(registry);
    }

    private double count(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? Double.NaN : value.doubleValue();
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }
}
