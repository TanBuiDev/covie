package vn.covie.orders.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan05SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void v010CreatesLifecycleTablesAndExpandsConstraints() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "order_cancellation_requests")).isTrue();
            assertThat(tableExists(connection, "order_issues")).isTrue();
            assertThat(columnLength(connection, "order_events", "event_type")).isGreaterThanOrEqualTo(80);
            assertThat(constraintExists(connection, "orders", "ck_order_status_v010")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet results = connection.getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return results.next();
        }
    }

    private int columnLength(Connection connection, String table, String column) throws Exception {
        try (ResultSet results = connection.getMetaData().getColumns(null, null, table, column)) {
            if (!results.next()) {
                throw new IllegalStateException("Missing column: " + table + "." + column);
            }
            return results.getInt("COLUMN_SIZE");
        }
    }

    private boolean constraintExists(Connection connection, String table, String constraint) throws Exception {
        try (var statement = connection.prepareStatement("""
                select 1 from pg_constraint pc
                join pg_class t on t.oid = pc.conrelid
                where t.relname = ? and pc.conname = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }
}
