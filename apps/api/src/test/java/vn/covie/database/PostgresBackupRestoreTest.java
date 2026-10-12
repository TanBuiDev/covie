package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;

class PostgresBackupRestoreTest {
    @Test
    void actualScriptsRestoreLatestSchemaAndSelectedDataIntoEmptyDisposableDatabase() throws Exception {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("backup_source")
                .withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID(), address = UUID.randomUUID();
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Backup user','ACTIVE',now(),now())", user);
            jdbc.update("insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version) values (?,?,'Home','Backup recipient','0900000001','Backup street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS',true,'ACTIVE',now(),now(),0)", address, user);
            var expectedUser = jdbc.queryForMap("select * from users where id=?", user);
            String addressQuery = "select id,user_id,label,recipient_name,phone,address_text,ST_AsText(geo_point::geometry) as point,pin_source,is_default,status,created_at,updated_at,version from user_addresses where id=?";
            var expectedAddress = jdbc.queryForMap(addressQuery, address);
            var expectedCounts = counts(jdbc);
            for (String script : new String[]{"postgres-backup.sh", "postgres-restore.sh", "verify-backup.sh", "verify-dump.sh"}) {
                String content = Files.readString(Path.of("../../infra/backup", script)).replace("\r\n", "\n");
                postgres.copyFileToContainer(Transferable.of(content.getBytes(StandardCharsets.UTF_8), 0644), "/tmp/" + script);
            }
            var backup = command(postgres, "backup_source", "bash", "/tmp/postgres-backup.sh");
            String dump = backup.lines().filter(line -> line.startsWith("Backup created: "))
                    .map(line -> line.substring("Backup created: ".length())).findFirst().orElseThrow();
            command(postgres, "backup_source", "bash", "/tmp/verify-dump.sh", dump);
            command(postgres, "backup_source", "cp", dump, "/tmp/no-checksum.dump");
            var missingChecksum = execute(postgres, "backup_source", "bash", "/tmp/verify-dump.sh", "/tmp/no-checksum.dump");
            assertThat(missingChecksum.getExitCode()).isNotZero();
            assertThat(missingChecksum.getStderr()).contains("Missing backup checksum");
            byte[] malformed = "Not a PostgreSQL archive.\n".getBytes(StandardCharsets.UTF_8);
            String malformedHash = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(malformed));
            postgres.copyFileToContainer(Transferable.of(malformed, 0600), "/tmp/malformed.dump");
            postgres.copyFileToContainer(Transferable.of((malformedHash + "  malformed.dump\n")
                    .getBytes(StandardCharsets.UTF_8), 0600), "/tmp/malformed.dump.sha256");
            var malformedArchive = execute(postgres, "backup_source", "bash", "/tmp/verify-dump.sh", "/tmp/malformed.dump");
            assertThat(malformedArchive.getExitCode()).isNotZero();
            assertThat(malformedArchive.getStderr()).contains("pg_restore:");
            jdbc.execute("create database backup_target");
            command(postgres, "backup_target", "bash", "/tmp/postgres-restore.sh", dump);
            var target = new DriverManagerDataSource(postgres.getJdbcUrl().replace("/backup_source", "/backup_target"),
                    postgres.getUsername(), postgres.getPassword());
            var restored = new JdbcTemplate(target);
            assertThat(restored.queryForMap("select * from users where id=?", user)).isEqualTo(expectedUser);
            assertThat(restored.queryForMap(addressQuery, address)).isEqualTo(expectedAddress);
            assertThat(counts(restored)).isEqualTo(expectedCounts);
            var flyway = Flyway.configure().dataSource(target).locations("classpath:db/migration").load();
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            verifyRestoredRuntime(target.getUrl(), user, address);
            command(postgres, "backup_target", "bash", "/tmp/verify-backup.sh");
            var refusal = execute(postgres, "backup_target", "bash", "/tmp/postgres-restore.sh", dump);
            assertThat(refusal.getExitCode()).isNotZero();
            assertThat(refusal.getStderr()).contains("Target database is not empty");
            var productionRefusal = execute(postgres, "covie", "bash", "/tmp/postgres-restore.sh", dump);
            assertThat(productionRefusal.getExitCode()).isNotZero();
            assertThat(productionRefusal.getStderr()).contains("Refusing restore into production-named database");
            var confirmationRefusal = execute(postgres, "backup_target", "env", "COVIE_RESTORE_CONFIRM=NO",
                    "bash", "/tmp/postgres-restore.sh", dump);
            assertThat(confirmationRefusal.getExitCode()).isNotZero();
            assertThat(confirmationRefusal.getStderr()).contains("Restore confirmation rejected");
            jdbc.execute("create database backup_checksum_target");
            postgres.copyFileToContainer(Transferable.of(("0".repeat(64) + "  fixture.dump\n")
                    .getBytes(StandardCharsets.UTF_8), 0600), dump + ".sha256");
            var invalidDump = execute(postgres, "backup_source", "bash", "/tmp/verify-dump.sh", dump);
            assertThat(invalidDump.getExitCode()).isNotZero();
            assertThat(invalidDump.getStderr()).contains("Backup checksum mismatch");
            var checksumRefusal = execute(postgres, "backup_checksum_target", "bash", "/tmp/postgres-restore.sh", dump);
            assertThat(checksumRefusal.getExitCode()).isNotZero();
            assertThat(checksumRefusal.getStderr()).contains("Backup checksum mismatch");
            var rejected = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl().replace("/backup_source", "/backup_checksum_target"),
                    postgres.getUsername(), postgres.getPassword()));
            assertThat(counts(rejected)).isEmpty();
            assertThat(counts(jdbc)).isEqualTo(expectedCounts);
            assertThat(jdbc.queryForMap("select * from users where id=?", user)).isEqualTo(expectedUser);
        }
    }

    private void verifyRestoredRuntime(String jdbcUrl, UUID user, UUID address) throws Exception {
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(vn.covie.CovieApplication.class)
                .run("--spring.profiles.active=test", "--server.port=0", "--management.server.port=0",
                        "--spring.datasource.url=" + jdbcUrl, "--spring.datasource.username=test",
                        "--spring.datasource.password=test-only", "--covie.auth.jwt.private-key-path=",
                        "--covie.auth.jwt.public-key-path=", "--covie.rate-limit.enabled=false",
                        "--covie.retention.enabled=false", "--covie.outbox.max-events-per-poll=0",
                        "--covie.bootstrap.reference-data.enabled=false", "--covie.bootstrap.first-admin-email=")) {
            assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(jdbcUrl);
            var issuer = context.getBean(vn.covie.auth.application.InitialSessionIssuer.class);
            String bearer = "Bearer " + issuer.issue(user,
                    new vn.covie.auth.model.DeviceMetadata("Restore test", "restore-test")).accessToken().value();
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            var anonymous = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/me")).timeout(java.time.Duration.ofSeconds(5)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(401);
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            for (String path : java.util.List.of("/api/v1/me", "/api/v1/me/addresses")) {
                var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://127.0.0.1:" + port + path)).header("Authorization", bearer)
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(path).isEqualTo(200);
                var body = json.readTree(response.body());
                if (path.equals("/api/v1/me")) {
                    assertThat(body.path("id").asText()).isEqualTo(user.toString());
                    assertThat(body.path("displayName").asText()).isEqualTo("Backup user");
                } else {
                    assertThat(body.size()).isEqualTo(1);
                    assertThat(body.get(0).path("id").asText()).isEqualTo(address.toString());
                    assertThat(body.get(0).path("addressText").asText()).isEqualTo("Backup street");
                }
            }
        }
    }

    private Map<String, Long> counts(JdbcTemplate jdbc) {
        var counts = new LinkedHashMap<String, Long>();
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' order by tablename", String.class)) {
            counts.put(table, jdbc.queryForObject("select count(*) from \"" + table + "\"", Long.class));
        }
        return counts;
    }

    private String command(PostgreSQLContainer<?> postgres, String database, String... arguments) throws Exception {
        var result = execute(postgres, database, arguments);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout();
    }

    private org.testcontainers.containers.Container.ExecResult execute(PostgreSQLContainer<?> postgres,
            String database, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>(java.util.List.of("env", "PGHOST=127.0.0.1", "PGPORT=5432",
                "PGDATABASE=" + database, "PGUSER=test", "PGPASSWORD=test-only", "COVIE_BACKUP_DIR=/tmp/backup-dumps",
                "COVIE_RESTORE_CONFIRM=RESTORE"));
        command.addAll(java.util.List.of(arguments));
        return postgres.execInContainer(command.toArray(String[]::new));
    }
}
