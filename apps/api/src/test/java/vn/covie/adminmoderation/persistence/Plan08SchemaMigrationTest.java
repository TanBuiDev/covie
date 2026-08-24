package vn.covie.adminmoderation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class Plan08SchemaMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("covie_test")
            .withUsername("covie")
            .withPassword("covie_test");

    @Test
    void v013CreatesAdminModerationAndSettingsSchema() throws Exception {
        POSTGRES.start();
        try (Connection connection = POSTGRES.createConnection("")) {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            assertThat(currentFlywayVersion(connection)).isEqualTo("013");
            for (String table : new String[] {
                    "system_settings", "system_setting_history", "moderation_cases", "moderation_actions" }) {
                assertThat(tableExists(connection, table)).as(table).isTrue();
            }
            assertThat(columnExists(connection, "need_requests", "moderation_status")).isTrue();
            assertThat(columnExists(connection, "merchant_profiles", "status_before_suspension")).isTrue();
        } finally {
            POSTGRES.stop();
        }
    }

    private String currentFlywayVersion(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                select version from flyway_schema_history
                where success = true order by installed_rank desc limit 1
                """)) {
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getString(1);
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            statement.setString(1, table);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getBoolean(1);
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (var statement = connection.prepareStatement("""
                select exists (
                    select 1 from information_schema.columns
                    where table_schema = 'public' and table_name = ? and column_name = ?
                )
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getBoolean(1);
            }
        }
    }
}
