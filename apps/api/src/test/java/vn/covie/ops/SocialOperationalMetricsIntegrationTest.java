package vn.covie.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SocialOperationalMetricsIntegrationTest {
    @Test void exposesCachedBoundedSocialMeasurementsWithoutChangingBusinessRows() {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("social_metrics")
                .withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).locations("classpath:db/migration")
                    .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            var jdbc = new JdbcTemplate(source);
            var registry = new SimpleMeterRegistry();
            try {
                var metrics = new DatabaseOperationalMetrics(jdbc);
                metrics.bindTo(registry);
                metrics.refresh();
                assertThat(registry.get("covie.moderation.open.cases").gauge().value()).isZero();
                assertThat(registry.get("covie.moderation.oldest.open.seconds").gauge().value()).isZero();
                assertThat(registry.get("covie.media.ready.declared.bytes").gauge().value()).isZero();
                UUID user = UUID.randomUUID();
                jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Metric fixture','ACTIVE',now(),now())", user);
                for (String status : java.util.List.of("OPEN", "RESOLVED", "CLOSED")) {
                    jdbc.update("insert into moderation_cases(id,case_type,target_type,target_user_id,reason_code,case_status,opened_at) values (?,'SYSTEM_FLAG','USER',?,'METRIC',?,now()-interval '2 minutes')", UUID.randomUUID(), user, status);
                }
                for (String status : java.util.List.of("READY", "PENDING_UPLOAD", "FAILED", "DELETED")) {
                    jdbc.update("insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,created_at) values (?,'MINIO',?,'CATALOG_IMAGE','image/png',128,?,now(),now())", UUID.randomUUID(), "metric/" + UUID.randomUUID(), status);
                }
                jdbc.update("insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,created_at,deleted_at) values (?,'MINIO',?,'CATALOG_IMAGE','image/png',256,'READY',now(),now(),now())", UUID.randomUUID(), "metric/" + UUID.randomUUID());
                var cases = jdbc.queryForList("select to_jsonb(original)::text from moderation_cases original order by id", String.class);
                var assets = jdbc.queryForList("select to_jsonb(original)::text from media_assets original order by id", String.class);
                metrics.refresh();
                assertThat(registry.get("covie.moderation.open.cases").gauge().value()).isEqualTo(1);
                assertThat(registry.get("covie.moderation.oldest.open.seconds").gauge().value()).isBetween(120.0, 150.0);
                assertThat(registry.get("covie.media.ready.declared.bytes").gauge().value()).isEqualTo(128);
                assertThat(jdbc.queryForList("select to_jsonb(original)::text from moderation_cases original order by id", String.class)).isEqualTo(cases);
                assertThat(jdbc.queryForList("select to_jsonb(original)::text from media_assets original order by id", String.class)).isEqualTo(assets);
                jdbc.execute("drop table media_assets cascade");
                assertThat(registry.get("covie.media.ready.declared.bytes").gauge().value()).isEqualTo(128);
                metrics.refresh();
                assertThat(registry.get("covie.media.ready.declared.bytes").gauge().value()).isNaN();
                assertThat(registry.get("covie.moderation.open.cases").gauge().value()).isEqualTo(1);
                for (String meter : java.util.List.of("covie.moderation.open.cases", "covie.moderation.oldest.open.seconds", "covie.media.ready.declared.bytes")) {
                    assertThat(registry.get(meter).gauge().getId().getTags()).isEmpty();
                }
            } finally {
                registry.close();
            }
        }
    }
}
