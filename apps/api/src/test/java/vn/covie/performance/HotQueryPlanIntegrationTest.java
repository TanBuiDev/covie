package vn.covie.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
class HotQueryPlanIntegrationTest {
    @Test
    void productionLikeDatabaseHasExpectedMigrationFixtureAndHealthyHotPlans() throws Exception {
        String url = System.getenv("COVIE_PERF_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Set COVIE_PERF_JDBC_URL for the release performance gate");

        String user = System.getenv().getOrDefault("COVIE_PERF_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("COVIE_PERF_DB_PASSWORD", "");
        String queryFile = System.getenv("COVIE_PERF_QUERY_FILE");
        Assumptions.assumeTrue(queryFile != null && !queryFile.isBlank(), "Set COVIE_PERF_QUERY_FILE to exact repository SQL statements");

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertHighestMigration(connection);
            PerformanceFixtureBuilder.assertPilotScale(connection);

            String source = Files.readString(Path.of(queryFile));
            String[] statements = source.split("(?m)^-- COVIE-HOT-QUERY: .+$");
            int executed = 0;
            for (String raw : statements) {
                String sql = raw.trim();
                if (sql.isEmpty()) continue;
                assertFalse(sql.toLowerCase(Locale.ROOT).contains(" offset "), "Hot keyset query must not use OFFSET");
                String plan = explain(connection, sql);
                assertHealthyPlan(plan);
                executed++;
            }
            assertTrue(executed > 0, "At least one exact hot query must be supplied");
        }
    }

    static void assertHealthyPlan(String plan) throws Exception {
        assertFalse(plan.isBlank(), "EXPLAIN JSON must be returned");
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(plan);
        assertTrue(root.isArray() && !root.isEmpty() && root.get(0).path("Plan").isObject(),
                "EXPLAIN JSON must contain a query plan");
        assertFalse(hasSequentialScan(root, "outbox_events"),
                "Outbox hot query must not scan all historical rows");
        assertFalse(hasSequentialScan(root, "notifications"),
                "Notification hot query must use its user/cursor access path");
    }

    private static boolean hasSequentialScan(com.fasterxml.jackson.databind.JsonNode node, String relation) {
        if ("Seq Scan".equals(node.path("Node Type").asText())
                && relation.equals(node.path("Relation Name").asText())) return true;
        for (var child : node) if (hasSequentialScan(child, relation)) return true;
        return false;
    }

    private static void assertHighestMigration(Connection connection) throws Exception {
        try (var ps = connection.prepareStatement(
                "select coalesce(max(version),'') from flyway_schema_history where success")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(compareVersion(rs.getString(1), 13) >= 0,
                        "Plan 10 performance gate requires prerequisite Flyway V013 or a reviewed later migration");
            }
        }
    }

    private static int compareVersion(String value, int expected) {
        if (value == null || value.isBlank()) return -1;
        String digits = value.replaceAll("[^0-9].*$", "");
        try { return Integer.compare(Integer.parseInt(digits), expected); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String explain(Connection connection, String sql) throws Exception {
        try (var ps = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql);
             ResultSet rs = ps.executeQuery()) {
            StringBuilder out = new StringBuilder();
            while (rs.next()) out.append(rs.getString(1));
            return out.toString();
        }
    }
}
