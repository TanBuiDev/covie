package vn.covie.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class OutboxOperationalMetricsIntegrationTest {
    @Test
    void measuresOnlyDueAgeAndTerminalFailuresWithoutChangingRows() {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("outbox_metrics_test")
                .withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(source);
            var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try {
                var metrics = new DatabaseOperationalMetrics(jdbc);
                metrics.bindTo(registry);
                metrics.refresh();
                assertThat(registry.get("covie.outbox.oldest.due.seconds").gauge().value()).isZero();
                assertThat(registry.get("covie.outbox.terminal.failed").gauge().value()).isZero();
                insert(jdbc, "PENDING", 120, 0, null);
                insert(jdbc, "FAILED", 60, 1, "now()-interval '10 seconds'");
                insert(jdbc, "FAILED", 600, 1, "now()+interval '1 hour'");
                insert(jdbc, "FAILED", 900, 10, null);
                insert(jdbc, "FAILED", 900, 5, null);
                insert(jdbc, "PROCESSING", 1000, 0, null);
                insert(jdbc, "PUBLISHED", 1000, 0, null);
                var before = jdbc.queryForList("select * from outbox_events order by id");
                metrics.refresh();
                assertThat(registry.get("covie.outbox.oldest.due.seconds").gauge().value()).isBetween(120.0, 150.0);
                assertThat(registry.get("covie.outbox.terminal.failed").gauge().value()).isEqualTo(2);
                assertThat(registry.get("covie.outbox.backlog").gauge().value()).isEqualTo(5);
                assertThat(registry.get("covie.outbox.due").gauge().value()).isEqualTo(2);
                assertThat(jdbc.queryForList("select * from outbox_events order by id")).isEqualTo(before);
                assertThat(registry.get("covie.outbox.oldest.due.seconds").gauge().getId().getTags()).isEmpty();
                assertThat(registry.get("covie.outbox.terminal.failed").gauge().getId().getTags()).isEmpty();
            } finally { registry.close(); }
        }
    }

    private void insert(JdbcTemplate jdbc, String status, int age, int attempts, String retry) {
        jdbc.update("insert into outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at,status,attempts,next_attempt_at) "
                + "values (?,'METRIC_TEST','TEST',?,'{}'::jsonb,now()-make_interval(secs=>?),?,?,"
                + (retry == null ? "null" : retry) + ")", UUID.randomUUID(), UUID.randomUUID(), age, status, attempts);
    }
}
