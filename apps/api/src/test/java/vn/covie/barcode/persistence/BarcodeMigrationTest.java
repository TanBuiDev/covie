package vn.covie.barcode.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.support.PostgresIntegrationTest;

/** Runs migrations only through the shared disposable PostgreSQL test harness. */
class BarcodeMigrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void appliesV017MigrationSuccessfully() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='017' and success", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='barcode_reference_products'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select barcode_reference_valid_gtin('8934673501354')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select barcode_reference_valid_gtin('8934673501355')", Boolean.class)).isFalse();
    }
}
