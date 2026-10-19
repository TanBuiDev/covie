package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class CatalogVideoMetadataMigrationTest {
    @Test void addsCleanupBookkeepingWithoutChangingExistingMediaRows() {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("video_cleanup_upgrade").withUsername("test").withPassword("disposable-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("024")
                    .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            var jdbc = new JdbcTemplate(source);
            for (String purpose : new String[]{"CATALOG_IMAGE", "AD_BANNER", "CATALOG_VIDEO"})
                jdbc.update("insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,width,height,video_duration_ms,video_codec,media_status,upload_expires_at,verified_at,created_at) values (?,'S3',?,?,?,24,64,48,1000,'h264','READY',now(),now(),now())",
                        UUID.randomUUID(), "cleanup-upgrade/" + purpose, purpose, purpose.equals("CATALOG_IMAGE") ? "image/png" : "video/mp4");
            var before = jdbc.queryForList("select to_jsonb(asset)::text from media_assets asset order by id", String.class);
            Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("025")
                    .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            assertThat(jdbc.queryForList("select (to_jsonb(asset)-'storage_cleanup_attempted_at'-'storage_deleted_at')::text from media_assets asset order by id", String.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from media_assets where storage_cleanup_attempted_at is null and storage_deleted_at is null", Integer.class)).isEqualTo(3);
        }
    }

    @Test void upgradesExistingImageAndAdVideoWithoutChangingTheirDataAndRequiresVerifiedCatalogMetadata() {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("video_metadata_upgrade").withUsername("test").withPassword("disposable-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("022")
                    .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            var jdbc = new JdbcTemplate(source);
            for (String purpose : new String[] {"CATALOG_IMAGE", "AD_BANNER"})
                jdbc.update("insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,'S3',?,?,?,24,'READY',now(),now(),now())",
                        UUID.randomUUID(), "legacy/" + purpose, purpose, purpose.equals("AD_BANNER") ? "video/mp4" : "image/png");
            var before = jdbc.queryForList("select to_jsonb(original)::text from media_assets original order by id", String.class);
            Flyway.configure().dataSource(source).locations("classpath:db/migration-social").target("023")
                    .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            assertThat(jdbc.queryForList("select (to_jsonb(original)-'video_duration_ms'-'video_codec')::text from media_assets original order by id", String.class))
                    .isEqualTo(before);
            assertThatThrownBy(() -> jdbc.update("insert into media_assets(id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,'S3','invalid/catalog-video','CATALOG_VIDEO','video/mp4',24,'READY',now(),now(),now())", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select count(*) from media_assets", Long.class)).isEqualTo(2);
        }
    }
}
