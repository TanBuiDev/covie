package vn.covie.performance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contract for the production-like dataset used by Plan 10 performance gates.
 *
 * <p>The fixture is intentionally loaded with server-side/bulk tooling outside JPA. This helper
 * validates that a target database is large enough before planner assertions are trusted; tiny
 * developer databases must never produce a false green performance result.</p>
 */
final class PerformanceFixtureBuilder {
    private static final Map<String, Long> MINIMUM_ROWS = new LinkedHashMap<>();

    static {
        MINIMUM_ROWS.put("location_areas", 100L);
        MINIMUM_ROWS.put("merchant_profiles", 1_000L);
        MINIMUM_ROWS.put("merchant_locations", 2_000L);
        MINIMUM_ROWS.put("catalog_items", 20_000L);
        MINIMUM_ROWS.put("catalog_variants", 30_000L);
        MINIMUM_ROWS.put("location_variants", 40_000L);
        MINIMUM_ROWS.put("need_requests", 10_000L);
        MINIMUM_ROWS.put("orders", 50_000L);
        MINIMUM_ROWS.put("order_events", 200_000L);
        MINIMUM_ROWS.put("notifications", 100_000L);
        MINIMUM_ROWS.put("outbox_events", 50_000L);
    }

    private PerformanceFixtureBuilder() {}

    static void assertPilotScale(Connection connection) throws SQLException {
        for (Map.Entry<String, Long> requirement : MINIMUM_ROWS.entrySet()) {
            String table = requirement.getKey();
            if (!tableExists(connection, table)) {
                throw new IllegalStateException("Required performance table is missing: " + table);
            }
            long rows = count(connection, table);
            if (rows < requirement.getValue()) {
                throw new IllegalStateException(
                        "Performance fixture too small for " + table + ": " + rows + " < " + requirement.getValue());
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var ps = connection.prepareStatement("select to_regclass(?) is not null")) {
            ps.setString(1, "public." + table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        // table is from the constant allow-list above; never derived from external input.
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from \"" + table + "\"")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
