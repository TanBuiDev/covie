package vn.covie.trust;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;

/** Final database-level safety net for Plan 07 projections and the at-least-once outbox. */
class Plan07AcceptanceIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void freshPlan07SchemaHasNoActionableBacklogOrBrokenProjectionInvariants() {
        assertThat(count("""
                select count(*) from outbox_events
                where status = 'FAILED' and next_attempt_at <= now()
                """)).isZero();
        assertThat(rows("""
                select source_outbox_event_id, user_id, notification_type, count(*)
                from notifications where source_outbox_event_id is not null
                group by source_outbox_event_id, user_id, notification_type having count(*) > 1
                """)).isEmpty();
        assertThat(rows("select order_id, count(*) from reputation_order_outcomes group by order_id having count(*) > 1")).isEmpty();
        assertThat(count("""
                select count(*) from user_reputation_stats
                where eligible_order_count < 0 or completed_order_count < 0
                   or completion_rate_basis_points not between 0 and 10000
                """)).isZero();
        assertThat(count("""
                select count(*) from merchant_reputation_stats
                where eligible_order_count < 0 or completed_order_count < 0
                   or completion_rate_basis_points not between 0 and 10000
                """)).isZero();
    }

    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private List<java.util.Map<String, Object>> rows(String sql) { return jdbc.queryForList(sql); }
}
