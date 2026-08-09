package vn.covie.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class AuthSchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void identityAndAuthTablesExistAfterFlyway() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "users")).isTrue();
            assertThat(tableExists(connection, "user_identities")).isTrue();
            assertThat(tableExists(connection, "system_role_assignments")).isTrue();
            assertThat(tableExists(connection, "user_addresses")).isTrue();
            assertThat(tableExists(connection, "audit_logs")).isTrue();
            assertThat(tableExists(connection, "auth_sessions")).isTrue();
            assertThat(tableExists(connection, "auth_refresh_tokens")).isTrue();
            assertThat(tableExists(connection, "auth_action_tickets")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement(
                "select exists(select 1 from information_schema.tables where table_schema='public' and table_name=?)")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }
}
