package vn.covie.merchantcatalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan03SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void createsMerchantCatalogMediaAndReliabilityTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "location_areas")).isTrue();
            assertThat(tableExists(c, "merchant_profiles")).isTrue();
            assertThat(tableExists(c, "merchant_locations")).isTrue();
            assertThat(tableExists(c, "media_assets")).isTrue();
            assertThat(tableExists(c, "catalog_items")).isTrue();
            assertThat(tableExists(c, "variants")).isTrue();
            assertThat(tableExists(c, "location_variants")).isTrue();
            assertThat(tableExists(c, "idempotency_records")).isTrue();
            assertThat(tableExists(c, "outbox_events")).isTrue();
        }
    }

    @Test
    void locationAndCatalogGeoSearchIndexesExist() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(indexExists(c, "ix_merchant_locations_geo")).isTrue();
            assertThat(indexExists(c, "ix_catalog_items_search_vector")).isTrue();
        }
    }

    private boolean tableExists(Connection c, String name) throws Exception {
        try (var ps = c.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private boolean indexExists(Connection c, String name) throws Exception {
        try (var ps = c.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
