package vn.covie.retention;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import vn.covie.ops.OperationalMetrics;

@Service
public final class TechnicalRetentionService {
    private static final long ADVISORY_LOCK = 591000010L;
    private final JdbcTemplate jdbc;
    private final TechnicalRetentionProperties properties;
    private final OperationalMetrics metrics;

    public TechnicalRetentionService(JdbcTemplate jdbc, TechnicalRetentionProperties properties, OperationalMetrics metrics) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.metrics = metrics;
    }

    public void sweepOnce() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            sweepOnConnection(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            return null;
        });
    }

    private void sweepOnConnection(JdbcTemplate jdbc) {
        Boolean locked = jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, ADVISORY_LOCK);
        if (!Boolean.TRUE.equals(locked)) return;
        try {
            Instant expiredCutoff = Instant.now().minus(properties.expiredRecordGrace());
            Instant outboxCutoff = Instant.now().minus(properties.publishedOutboxRetention());
            delete(jdbc, "idempotency", "delete from idempotency_records where ctid in (select ctid from idempotency_records where expires_at < ? order by expires_at limit ?)", expiredCutoff);
            delete(jdbc, "checkout_sessions", "delete from checkout_sessions where ctid in (select ctid from checkout_sessions where expires_at < ? order by expires_at limit ?)", expiredCutoff);
            delete(jdbc, "auth_action_tickets", "delete from auth_action_tickets where ctid in (select ctid from auth_action_tickets where expires_at < ? order by expires_at limit ?)", expiredCutoff);
            delete(jdbc, "auth_refresh_tokens", "delete from auth_refresh_tokens t where ctid in (select x.ctid from auth_refresh_tokens x where x.expires_at < ? and not exists (select 1 from auth_refresh_tokens child where child.replaced_by_token_id = x.id) order by x.expires_at limit ?)", expiredCutoff);
            delete(jdbc, "auth_sessions", "delete from auth_sessions s where ctid in (select x.ctid from auth_sessions x where x.expires_at < ? and not exists (select 1 from auth_refresh_tokens t where t.session_id = x.id) order by x.expires_at limit ?)", expiredCutoff);
            delete(jdbc, "outbox_published", "delete from outbox_events where ctid in (select ctid from outbox_events where status = 'PUBLISHED' and published_at < ? order by published_at limit ?)", outboxCutoff);
            metrics.retentionSuccess();
        } catch (RuntimeException ex) {
            metrics.retentionFailure();
            throw ex;
        } finally {
            jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean.class, ADVISORY_LOCK);
        }
    }

    private void delete(JdbcTemplate jdbc, String dataset, String sql, Instant cutoff) {
        int rows = jdbc.update(sql, Timestamp.from(cutoff), properties.batchSize());
        metrics.retentionDeleted(dataset, rows);
    }
}
