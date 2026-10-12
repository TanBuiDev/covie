package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;

/** Planner settings are transaction-local and rolled back by the test framework. */
@Transactional
@org.springframework.boot.test.context.SpringBootTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.performance.HotQueryPlanPostgresIntegrationTest$SqlCapture")
class HotQueryPlanPostgresIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.covie.notifications.persistence.NotificationRepository notifications;

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<String> SQL=new ThreadLocal<>();
        public SqlCapture() {}
        @Override public String inspect(String sql) {
            if(sql.startsWith("select ") && sql.contains(" from notifications ")) SQL.set(sql);
            return sql;
        }
    }

    @org.junit.jupiter.api.Test
    void actualNotificationCursorSqlUsesIndexWithOneHundredThousandRows() throws Exception {
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at,version) "
                +"select md5('perf-user-'||g)::uuid,'Perf user '||g,'ACTIVE',now(),now(),0 from generate_series(1,1000) g");
        jdbc.update("insert into notifications(id,user_id,notification_type,title,body,created_at) "
                +"select gen_random_uuid(),md5('perf-user-'||((g%1000)+1))::uuid,'ORDER_NEW','Perf','Fixture',"
                +"now()-g*interval '1 second' from generate_series(1,100000) g");
        jdbc.execute("analyze notifications");
        assertThat(jdbc.queryForObject("select count(*) from notifications",Long.class)).isGreaterThanOrEqualTo(100_000L);
        var user=jdbc.queryForObject("select md5('perf-user-1')::uuid",java.util.UUID.class);
        var cursorTime=java.time.Instant.now().minusSeconds(50_000);
        var cursorId=new java.util.UUID(0,0);
        SqlCapture.SQL.remove();
        String sql;
        try {
            var page=notifications.pageAfter(user,cursorTime,cursorId,org.springframework.data.domain.PageRequest.of(0,31));
            assertThat(page).hasSize(31);
            sql=SqlCapture.SQL.get();
            assertThat(sql).isNotBlank();
            assertThat(sql.chars().filter(character->character=='?').count()).isEqualTo(5);
        } finally { SqlCapture.SQL.remove(); }
        String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,String.class,
                user,java.sql.Timestamp.from(cursorTime),java.sql.Timestamp.from(cursorTime),cursorId,31);
        HotQueryPlanIntegrationTest.assertHealthyPlan(plan);
        assertThat(plan).contains("ix_notifications_user_cursor");
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../.local/perf-notification-cursor-sql-20261009.sql"),sql);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../.local/perf-notification-cursor-plan-20261009.json"),plan);
        SqlCapture.SQL.remove();
        String firstSql;
        try {
            var firstPage=notifications.findByUserIdOrderByCreatedAtDescIdDesc(user,
                    org.springframework.data.domain.PageRequest.of(0,31));
            assertThat(firstPage).hasSize(31);
            firstSql=SqlCapture.SQL.get();
            assertThat(firstSql).isNotBlank();
            assertThat(firstSql.chars().filter(character->character=='?').count()).isEqualTo(2);
        } finally { SqlCapture.SQL.remove(); }
        String firstPlan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+firstSql,
                String.class,user,31);
        HotQueryPlanIntegrationTest.assertHealthyPlan(firstPlan);
        assertThat(firstPlan).contains("ix_notifications_user_cursor");
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../.local/perf-notification-first-sql-20261009.sql"),firstSql);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../.local/perf-notification-first-plan-20261009.json"),firstPlan);
    }

    @org.junit.jupiter.api.Test
    void exactOutboxRepositoryQueryUsesIndexWithFiftyThousandHistoricalEvents() throws Exception {
        jdbc.update("insert into outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at,status,attempts,next_attempt_at,published_at) "
                + "select gen_random_uuid(),'PERF_FIXTURE','PERF',gen_random_uuid(),'{}'::jsonb,"
                + "now()-g*interval '1 second',case when g<=100 then 'PENDING' else 'PUBLISHED' end,0,null,"
                + "case when g>100 then now() else null end from generate_series(1,50000) g");
        jdbc.execute("analyze outbox_events");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events",Long.class)).isGreaterThanOrEqualTo(50_000L);
        var method=vn.covie.shared.outbox.OutboxEventRepository.class.getMethod("lockNextDue",java.time.Instant.class);
        String sql=method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertThat(sql).contains("for update skip locked");
        String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql.replace(":now","?"),
                String.class,java.sql.Timestamp.from(java.time.Instant.now()));
        HotQueryPlanIntegrationTest.assertHealthyPlan(plan);
        var output=java.nio.file.Path.of("../../.local/perf-outbox-exact-plan-20261009.json");
        java.nio.file.Files.createDirectories(output.toAbsolutePath().getParent());
        java.nio.file.Files.writeString(output,plan);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notifications", "outbox_events"})
    void rejectsActualPostgresSequentialPlan(String table) {
        jdbc.execute("set local enable_indexscan=off");
        jdbc.execute("set local enable_indexonlyscan=off");
        jdbc.execute("set local enable_bitmapscan=off");
        String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) select * from "+table,String.class);
        assertThat(plan).contains("Seq Scan");
        assertThatThrownBy(()->HotQueryPlanIntegrationTest.assertHealthyPlan(plan))
                .isInstanceOf(AssertionError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notifications", "outbox_events"})
    void acceptsActualPostgresIndexedPlan(String table) {
        jdbc.execute("set local enable_seqscan=off");
        String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) select * from "+table
                +" where id='00000000-0000-0000-0000-000000000000'::uuid",String.class);
        assertThat(plan).contains("Index");
        assertThatCode(()->HotQueryPlanIntegrationTest.assertHealthyPlan(plan)).doesNotThrowAnyException();
    }
}
