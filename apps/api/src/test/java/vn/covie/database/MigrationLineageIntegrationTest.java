package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class MigrationLineageIntegrationTest {
    @Test
    void upgradesKnownHistoriesWithoutChangingExistingRowsAndRejectsUnknownOrChangedHistory() {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("lineage_test")
                .withUsername("test").withPassword("test-only")) {
            postgres.start();
            var root = new JdbcTemplate(dataSource(postgres, "lineage_test"));
            List<String> expectedSchema = null;
            for (String stage : List.of("fresh", "common", "billing_partial", "billing", "social")) {
                root.execute("create database " + stage);
                var source = dataSource(postgres, stage);
                var jdbc = new JdbcTemplate(source);
                if (!stage.equals("fresh")) {
                    String location = stage.equals("social") ? "classpath:db/migration-social" : "classpath:db/migration";
                    String target = switch (stage) {
                        case "common" -> "019";
                        case "billing_partial" -> "020";
                        case "billing" -> "034";
                        default -> "025";
                    };
                    Flyway.configure().dataSource(source).locations(location).target(target)
                            .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
                    jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Preserved fixture','ACTIVE',now(),now())", UUID.randomUUID());
                }
                var columns = existingColumns(jdbc);
                var before = snapshot(jdbc, columns);
                var history = stage.equals("fresh") ? List.<Map<String, Object>>of()
                        : jdbc.queryForList("select * from flyway_schema_history order by installed_rank");
                var configuration = Flyway.configure().dataSource(source)
                        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
                new MigrationLineageConfiguration().customize(configuration);
                var flyway = configuration.load();
                flyway.migrate();
                flyway.validate();
                assertThat(snapshot(jdbc, columns)).as(stage + " old rows").isEqualTo(before);
                assertThat(jdbc.queryForList("select * from flyway_schema_history where installed_rank <= ? order by installed_rank", history.size()))
                        .as(stage + " original history").isEqualTo(history);
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("040");
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                List<String> schema = jdbc.queryForList("""
                        select 'column:' || table_name || ':' || column_name || ':' || data_type || ':' || is_nullable || ':' || coalesce(column_default,'')
                        from information_schema.columns where table_schema='public' and table_name<>'flyway_schema_history'
                        union all select 'constraint:' || rel.relname || ':' || con.conname || ':' || pg_get_constraintdef(con.oid)
                        from pg_constraint con join pg_class rel on rel.oid=con.conrelid join pg_namespace ns on ns.oid=rel.relnamespace
                        where ns.nspname='public' and rel.relname<>'flyway_schema_history'
                        union all select 'index:' || tablename || ':' || indexname || ':' || indexdef from pg_indexes
                        where schemaname='public' and tablename<>'flyway_schema_history' order by 1
                        """, String.class);
                if (expectedSchema == null) expectedSchema = schema;
                else assertThat(schema).as(stage + " combined schema").isEqualTo(expectedSchema);
            }
            for (String corruption : List.of("unknown", "checksum")) {
                root.execute("create database " + corruption);
                var source = dataSource(postgres, corruption);
                Flyway.configure().dataSource(source).configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).locations("classpath:db/migration").target("020").load().migrate();
                var jdbc = new JdbcTemplate(source);
                if (corruption.equals("unknown")) jdbc.update("update flyway_schema_history set script='V020__unknown.sql' where version='020'");
                else jdbc.update("update flyway_schema_history set checksum=checksum+1 where version='020'");
                var history = jdbc.queryForList("select * from flyway_schema_history order by installed_rank");
                var columns = existingColumns(jdbc);
                var before = snapshot(jdbc, columns);
                assertThatThrownBy(() -> {
                    var configuration = Flyway.configure().dataSource(source);
                    new MigrationLineageConfiguration().customize(configuration);
                    configuration.load().migrate();
                }).isInstanceOf(RuntimeException.class);
                assertThat(jdbc.queryForList("select * from flyway_schema_history order by installed_rank")).isEqualTo(history);
                assertThat(snapshot(jdbc, columns)).isEqualTo(before);
                assertThat(existingColumns(jdbc)).isEqualTo(columns);
            }
        }
    }

    private DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres, String database) {
        return new DriverManagerDataSource(postgres.getJdbcUrl().replace("/lineage_test", "/" + database),
                postgres.getUsername(), postgres.getPassword());
    }

    private Map<String, List<String>> existingColumns(JdbcTemplate jdbc) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','spatial_ref_sys') order by tablename", String.class)) {
            result.put(table, jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=? order by ordinal_position", String.class, table));
        }
        return result;
    }

    private Map<String, List<String>> snapshot(JdbcTemplate jdbc, Map<String, List<String>> columns) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        columns.forEach((table, fields) -> result.put(table, jdbc.queryForList("select to_jsonb(snapshot)::text from (select "
                + String.join(",", fields.stream().map(field -> "\"" + field + "\"").toList())
                + " from public.\"" + table + "\") snapshot order by 1", String.class)));
        return result;
    }
}
