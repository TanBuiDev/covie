package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;

class FoundationMigrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void installsRequiredPostgresExtensions() {
        List<String> extensions = jdbcTemplate.queryForList(
                "select extname from pg_extension where extname in ('postgis', 'pg_trgm', 'unaccent') order by extname",
                String.class);

        assertThat(extensions).containsExactly("pg_trgm", "postgis", "unaccent");
    }

    @Test
    void recordsFoundationMigrationInFlywayHistory() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '001' and success = true",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
