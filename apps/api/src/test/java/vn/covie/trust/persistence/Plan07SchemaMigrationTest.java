package vn.covie.trust.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.support.PostgresIntegrationTest;

class Plan07SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void createsPlan07TablesAtV012() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(currentFlywayVersion(connection)).isEqualTo("014");
            for (String table : new String[] {
                    "reviews",
                    "catalog_item_rating_stats",
                    "merchant_rating_stats",
                    "reputation_order_outcomes",
                    "user_reputation_stats",
                    "merchant_reputation_stats",
                    "notifications",
                    "notification_deliveries" }) {
                assertThat(tableExists(connection, table)).as(table).isTrue();
            }
        }
    }

    @Test
    void enforcesPlan07ProjectionAndNotificationInvariants() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, """
                    insert into users(id, display_name, account_status, created_at, updated_at, version)
                    values ('%s', 'buyer', 'ACTIVE', now(), now(), 0)
                    """.formatted(userId));
            execute(connection, """
                    insert into outbox_events(id, event_type, aggregate_type, aggregate_id, payload, occurred_at, status)
                    values ('%s', 'REVIEW_CREATED', 'REVIEW', '%s', '{}', now(), 'PENDING')
                    """.formatted(eventId, UUID.randomUUID()));

            assertThat(uniqueConstraintCount(connection, "reviews")).isGreaterThan(0);
            assertThat(indexExists(connection, "ux_notification_source_recipient_type")).isTrue();
            assertThatThrownBy(() -> execute(connection, """
                    insert into user_reputation_stats(user_id, completion_rate_basis_points, updated_at)
                    values ('%s', 10001, now())
                    """.formatted(userId))).isInstanceOf(Exception.class);

            String notification = """
                    insert into notifications(id, user_id, notification_type, title, body, source_outbox_event_id, created_at)
                    values ('%s', '%s', 'REVIEW_RECEIVED', 'Review', 'A review was created', '%s', now())
                    """;
            execute(connection, notification.formatted(UUID.randomUUID(), userId, eventId));
            assertThatThrownBy(() -> execute(connection,
                    notification.formatted(UUID.randomUUID(), userId, eventId))).isInstanceOf(Exception.class);
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

    private int uniqueConstraintCount(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement("""
                select count(*) from information_schema.table_constraints
                where table_name = ? and constraint_type = 'UNIQUE'
                """)) {
            statement.setString(1, table);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private boolean indexExists(Connection connection, String index) throws Exception {
        try (var statement = connection.prepareStatement("select to_regclass('public.' || ?) is not null")) {
            statement.setString(1, index);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getBoolean(1);
            }
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
