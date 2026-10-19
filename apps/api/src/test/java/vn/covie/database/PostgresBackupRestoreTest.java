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
            BillingFixture billing = seedBilling(jdbc, user);
            var expectedData = snapshot(jdbc);
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
            assertThat(snapshot(restored)).isEqualTo(expectedData);
            var flyway = Flyway.configure().dataSource(target).locations("classpath:db/migration").load();
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            for (int restart = 0; restart < 2; restart++) {
                verifyRestoredRuntime(target.getUrl(), user, address, billing);
                assertThat(snapshot(restored)).containsAllEntriesOf(financialRows(expectedData));
            }
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
            assertThat(snapshot(jdbc)).isEqualTo(expectedData);
        }
    }

    private void verifyRestoredRuntime(String jdbcUrl, UUID user, UUID address, BillingFixture billing) throws Exception {
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(vn.covie.CovieApplication.class)
                .run("--spring.profiles.active=test", "--server.port=0", "--management.server.port=0",
                        "--spring.datasource.url=" + jdbcUrl, "--spring.datasource.username=test",
                        "--spring.datasource.password=test-only", "--covie.auth.jwt.private-key-path=",
                        "--covie.auth.jwt.public-key-path=", "--covie.rate-limit.enabled=false",
                        "--covie.retention.enabled=false", "--covie.outbox.max-events-per-poll=0",
                        "--covie.bootstrap.reference-data.enabled=false", "--covie.bootstrap.first-admin-email=",
                        "--PAYOS_PAYMENT_ENABLED=false", "--COVIE_BILLING_RELEASE_APPROVED=false",
                        "--COVIE_BILLING_RECONCILE_ENABLED=false")) {
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
            var invoices = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/merchants/" + billing.merchant() + "/billing/invoices"))
                    .header("Authorization", bearer).timeout(java.time.Duration.ofSeconds(5)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(invoices.statusCode()).isEqualTo(200);
            assertThat(invoices.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            var ledger = json.readTree(invoices.body());
            assertThat(ledger.size()).isEqualTo(3);
            for (var invoice : ledger) {
                UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
                assertThat(billing.invoices()).containsKey(invoiceId);
                assertThat(invoice.path("status").asText()).isEqualTo(billing.invoices().get(invoiceId));
                assertThat(invoice.path("amountVnd").asLong()).isEqualTo(10000);
            }
        }
    }

    private BillingFixture seedBilling(JdbcTemplate jdbc, UUID user) {
        UUID merchant = UUID.randomUUID();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'USER',now())", UUID.randomUUID(), user);
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,'Backup merchant',?,'ACTIVE','APPROVED',now(),now(),0)", merchant, "backup-" + merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) values (?,?,?,'OWNER','ACTIVE',now(),now(),0)", UUID.randomUUID(), merchant, user);
        Map<UUID, String> invoices = new LinkedHashMap<>();
        long orderCode = 4100000000L;
        for (String status : new String[]{"PAID", "PENDING_PAYMENT", "REFUNDED"}) {
            UUID invoice = UUID.randomUUID();
            invoices.put(invoice, status);
            jdbc.update("""
                insert into billing_invoices(id,invoice_number,merchant_id,plan_version_id,policy_version_id,status,
                    amount_vnd,period_start,period_end,pricing_snapshot,idempotency_key,request_fingerprint,
                    created_at,expires_at,paid_at,payos_order_code,grace_until)
                select ?, ?, ?, v.id, p.id, ?, 10000, now()-interval '1 day',now()+interval '29 days',
                    '{"priceVnd":10000,"planCode":"SELLER_STANDARD","note":"Bảo toàn lịch sử fixture"}'::jsonb,
                    ?,repeat('a',64),now()-interval '1 day',now()+interval '15 minutes',
                    case when ?='PENDING_PAYMENT' then null else now()-interval '1 day' end,?,now()+interval '32 days'
                from billing_plan_versions v cross join billing_policy_versions p
                where v.plan_code='SELLER_STANDARD' and v.revision=1 and p.revision=1
                """, invoice, "BACKUP-" + status, merchant, status, "backup-key-" + status, status, orderCode);
            String attemptStatus = status.equals("PAID") ? "SUCCEEDED" : status.equals("REFUNDED") ? "REFUNDED" : "PENDING";
            jdbc.update("""
                insert into billing_payment_attempts(id,invoice_id,provider,provider_order_id,provider_transaction_id,
                    status,expected_amount_vnd,received_amount_vnd,created_at,settled_at,provider_payment_link_id,checkout_url,qr_payload)
                values (?,?,'PAYOS',?,?,?,10000,?,now()-interval '1 day',
                    case when ?='PENDING' then null else now()-interval '1 day' end,?,?,'fixture-qr-no-transfer')
                """, UUID.randomUUID(), invoice, Long.toString(orderCode), status.equals("PENDING_PAYMENT") ? null : "backup-reference-" + orderCode,
                attemptStatus, status.equals("PENDING_PAYMENT") ? null : 10000, attemptStatus,
                String.format("%032x", orderCode), "https://pay.payos.vn/web/" + String.format("%032x", orderCode));
            if (!status.equals("PENDING_PAYMENT")) {
                jdbc.update("""
                    insert into billing_entitlement_grants(id,merchant_id,source_type,source_id,valid_from,valid_until,revoked_at,created_at)
                    values (?,?,'PAID_INVOICE',?,now()-interval '1 day',now()+interval '29 days',
                        case when ?='REFUNDED' then now() else null end,now()-interval '1 day')
                    """, UUID.randomUUID(), merchant, invoice, status);
            } else {
                jdbc.update("insert into billing_reconciliation_observations(invoice_id,provider,provider_status,discrepancy,last_checked_at,revision) values (?,'PAYOS','PENDING','NONE',now(),3)", invoice);
            }
            jdbc.update("""
                insert into billing_quotes(id,merchant_id,plan_version_id,amount_due_vnd,created_at,expires_at,consumed_by_idempotency_key)
                select ?,?,id,10000,now()-interval '1 minute',now()+interval '5 minutes',?
                from billing_plan_versions where plan_code='SELLER_STANDARD' and revision=1
                """, UUID.randomUUID(), merchant, "backup-key-" + status);
            if (status.equals("PAID")) {
                jdbc.update("insert into billing_finance_cases(id,invoice_id,merchant_id,kind,status,reason,created_by_user_id,created_at) values (?,?,?,'REFUND','REQUESTED','Backup support fixture',?,now())", UUID.randomUUID(), invoice, merchant, user);
            }
            orderCode++;
        }
        jdbc.update("""
            insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,grace_until,created_at,updated_at)
            values (?,'SELLER_STANDARD','ACTIVE',now()-interval '1 day',now()+interval '29 days',now()+interval '32 days',now(),now())
            """, merchant);
        jdbc.update("insert into billing_reminder_dispatches(merchant_id,period_end,days_before,created_at) values (?,now()+interval '29 days',2,now())", merchant);
        jdbc.update("insert into billing_webhook_inbox(id,provider,provider_event_id,payload_sha256,verified,processing_status,received_at,processed_at,attempt_count) values (?,'PAYOS','backup-event',repeat('b',64),true,'DONE',now(),now(),1)", UUID.randomUUID());
        jdbc.update("""
            insert into idempotency_records(id,actor_id,operation,idempotency_key_hash,request_hash,status,response_status,response_body,created_at,expires_at)
            values (?,?,'BACKUP_FIXTURE',repeat('c',64),repeat('d',64),'COMPLETED',200,'{"fixture":"durable"}'::jsonb,now(),now()+interval '24 hours')
            """, UUID.randomUUID(), user);
        return new BillingFixture(merchant, Map.copyOf(invoices));
    }

    private Map<String, String> financialRows(Map<String, String> rows) {
        Map<String, String> result = new LinkedHashMap<>();
        rows.forEach((table, content) -> {
            if (table.startsWith("billing_") || table.equals("merchant_subscriptions")
                    || table.equals("merchant_profiles") || table.equals("merchant_memberships")
                    || table.equals("idempotency_records") || table.equals("audit_logs") || table.equals("outbox_events"))
                result.put(table, content);
        });
        return result;
    }

    private Map<String, String> snapshot(JdbcTemplate jdbc) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename<>'spatial_ref_sys' order by tablename", String.class)) {
            rows.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from \"" + table + "\" entry", String.class));
        }
        return rows;
    }

    private record BillingFixture(UUID merchant, Map<UUID, String> invoices) {}

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
