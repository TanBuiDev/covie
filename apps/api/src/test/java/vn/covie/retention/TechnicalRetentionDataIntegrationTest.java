package vn.covie.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.ops.OperationalMetrics;
import vn.covie.support.PostgresIntegrationTest;

@Transactional
@SpringBootTest(properties = {"covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
class TechnicalRetentionDataIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"idempotency_records", "checkout_sessions", "auth_action_tickets"})
    void appliesBatchAndGraceToExpiredTechnicalRecords(String table) {
        jdbc.update("delete from " + table);
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at,version) values (?,'Retention fixture','ACTIVE',now(),now(),0)", user);
        for (int days : new int[]{10, 9, 8, 6, -1}) {
            UUID id = UUID.randomUUID();
            String hash = id.toString().replace("-", "").repeat(2);
            switch (table) {
                case "idempotency_records" -> jdbc.update("insert into idempotency_records(id,actor_id,operation,idempotency_key_hash,request_hash,status,created_at,expires_at) values (?,?,'fixture',?,?,'COMPLETED',now(),now()-make_interval(days=>?))", id, user, hash, hash, days);
                case "checkout_sessions" -> jdbc.update("insert into checkout_sessions(id,token_hash,user_id,source_type,source_context,request_snapshot,preview_snapshot,created_at,expires_at) values (?,?,?,'DIRECT','{}','{}','{}',now(),now()-make_interval(days=>?))", id, hash, user, days);
                case "auth_action_tickets" -> jdbc.update("insert into auth_action_tickets(id,purpose,token_hash,user_id,google_subject,email_normalized,google_email_verified,created_at,expires_at) values (?,'ONBOARDING',?,?,?,'fixture@example.test',true,now(),now()-make_interval(days=>?))", id, hash, user, id.toString(), days);
                default -> throw new IllegalArgumentException(table);
            }
        }
        service(2).sweepOnce();
        assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where expires_at < now()-interval '7 days'", Long.class)).isEqualTo(1);
        service(2).sweepOnce();
        assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where expires_at >= now()-interval '7 days'", Long.class)).isEqualTo(2);
    }

    @Test
    void boundsCleanupAndPreservesRecentAndUnpublishedOutboxEvents() {
        // Remove other tests' rows only inside this rollback transaction so the global batch has a controlled fixture.
        jdbc.update("delete from outbox_events");
        UUID aggregate = UUID.randomUUID();
        for (int i = 0; i < 3; i++) outbox(aggregate, "PUBLISHED", 40);
        outbox(aggregate, "PUBLISHED", 29);
        outbox(aggregate, "PENDING", 40);
        outbox(aggregate, "FAILED", 40);
        service(2).sweepOnce();
        assertThat(count("select count(*) from outbox_events where aggregate_id=? and status='PUBLISHED' and published_at < now()-interval '30 days'", aggregate)).isEqualTo(1);
        assertThat(count("select count(*) from outbox_events where aggregate_id=?", aggregate)).isEqualTo(4);
        service(2).sweepOnce();
        assertThat(count("select count(*) from outbox_events where aggregate_id=?", aggregate)).isEqualTo(3);
        assertThat(count("select count(*) from outbox_events where aggregate_id=? and status in ('PENDING','FAILED')", aggregate)).isEqualTo(2);
    }

    @Test
    void drainsRefreshChainWithoutDeletingReferencedTokensOrRecentSessions() {
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at,version) values (?,'Retention fixture','ACTIVE',now(),now(),0)", user);
        UUID session = session(user, 9);
        UUID newest = token(session, null, 9);
        UUID middle = token(session, newest, 9);
        UUID oldest = token(session, middle, 9);
        UUID recentSession = session(user, 6);
        UUID recentToken = token(recentSession, null, 6);
        var service = service(500);
        service.sweepOnce();
        assertThat(exists("auth_refresh_tokens", oldest)).isFalse();
        assertThat(exists("auth_refresh_tokens", middle)).isTrue();
        assertThat(exists("auth_refresh_tokens", newest)).isTrue();
        assertThat(exists("auth_sessions", session)).isTrue();
        service.sweepOnce();
        assertThat(exists("auth_refresh_tokens", middle)).isFalse();
        assertThat(exists("auth_refresh_tokens", newest)).isTrue();
        service.sweepOnce();
        assertThat(exists("auth_refresh_tokens", newest)).isFalse();
        assertThat(exists("auth_sessions", session)).isFalse();
        assertThat(exists("auth_sessions", recentSession)).isTrue();
        assertThat(exists("auth_refresh_tokens", recentToken)).isTrue();
    }

    private TechnicalRetentionService service(int batch) {
        return new TechnicalRetentionService(jdbc,
                new TechnicalRetentionProperties(true, batch, Duration.ofDays(7), Duration.ofDays(30)),
                mock(OperationalMetrics.class));
    }

    private void outbox(UUID aggregate, String status, int days) {
        jdbc.update("insert into outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at,status,published_at) values (?,'RETENTION_FIXTURE','fixture',?,'{}',now(),?,now()-make_interval(days=>?))",
                UUID.randomUUID(), aggregate, status, days);
    }

    private UUID session(UUID user, int days) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth_sessions(id,user_id,token_family_id,created_at,last_used_at,expires_at) values (?,?,?,now(),now(),now()-make_interval(days=>?))", id, user, UUID.randomUUID(), days);
        return id;
    }

    private UUID token(UUID session, UUID replacement, int days) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth_refresh_tokens(id,session_id,token_hash,replaced_by_token_id,expires_at,created_at) values (?,?,?,?,now()-make_interval(days=>?),now())",
                id, session, id.toString().replace("-", "").repeat(2), replacement, days);
        return id;
    }

    private boolean exists(String table, UUID id) {
        return count("select count(*) from " + table + " where id=?", id) == 1;
    }

    private long count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }
}
