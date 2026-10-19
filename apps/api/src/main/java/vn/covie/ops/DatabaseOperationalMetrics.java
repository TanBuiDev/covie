package vn.covie.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class DatabaseOperationalMetrics implements MeterBinder {
    private final JdbcTemplate jdbc;
    private record Snapshot(double backlog, double due, double age, double terminal, double expired,
            double moderationOpen, double moderationAge, double mediaBytes) {}
    private volatile Snapshot snapshot = new Snapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN);

    public DatabaseOperationalMetrics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("covie.outbox.backlog", this, m -> m.snapshot.backlog()).register(registry);
        Gauge.builder("covie.outbox.due", this, m -> m.snapshot.due()).register(registry);
        Gauge.builder("covie.outbox.oldest.due.seconds", this, m -> m.snapshot.age()).register(registry);
        Gauge.builder("covie.outbox.terminal.failed", this, m -> m.snapshot.terminal()).register(registry);
        Gauge.builder("covie.checkout.expired.sessions", this, m -> m.snapshot.expired()).register(registry);
        Gauge.builder("covie.moderation.open.cases", this, metrics -> metrics.snapshot.moderationOpen()).register(registry);
        Gauge.builder("covie.moderation.oldest.open.seconds", this, metrics -> metrics.snapshot.moderationAge()).register(registry);
        Gauge.builder("covie.media.ready.declared.bytes", this, metrics -> metrics.snapshot.mediaBytes())
                .description("Declared bytes of non-deleted READY media metadata, not physical provider usage")
                .register(registry);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${covie.metrics.database-refresh-ms:15000}")
    public void refresh() {
        String due = "status='PENDING' or (status='FAILED' and next_attempt_at is not null and next_attempt_at<=now())";
        snapshot = new Snapshot(
                count("select count(*) from outbox_events where status in ('PENDING','FAILED')"),
                count("select count(*) from outbox_events where " + due),
                measure("select greatest(0, coalesce(extract(epoch from (now()-min(occurred_at))),0)) from outbox_events where " + due),
                count("select count(*) from outbox_events where status='FAILED' and next_attempt_at is null"),
                count("select count(*) from checkout_sessions where consumed_at is null and expires_at < now()"),
                count("select count(*) from moderation_cases where case_status='OPEN'"),
                measure("select greatest(0, coalesce(extract(epoch from (now()-min(opened_at))),0)) from moderation_cases where case_status='OPEN'"),
                measure("select coalesce(sum(size_bytes),0)::double precision from media_assets where media_status='READY' and deleted_at is null"));
    }
    private double measure(String sql) {
        try {
            Double value = jdbc.queryForObject(sql, Double.class);
            return value == null ? Double.NaN : value;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
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

