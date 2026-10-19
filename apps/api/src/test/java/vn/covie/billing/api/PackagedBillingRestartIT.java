package vn.covie.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.billing.support.LoopbackPayosProvider;
import vn.covie.users.security.PasswordConfiguration;

class PackagedBillingRestartIT {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void killedPackagedApiRollsBackAndNewJvmRetriesSignedPaymentExactlyOnce() throws Exception {
        Path jar = Path.of(System.getProperty("covie.test.packaged-api-jar", "target/covie-api-0.1.0-SNAPSHOT.jar")).toAbsolutePath();
        verifyArtifact(jar);
        Path output = Files.createTempDirectory(Path.of("../../.local").toAbsolutePath().normalize(), "billing-jvm-restart-");
        Path privateKey = output.resolve("private.pem"), publicKey = output.resolve("public.pem");
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(3072);
        var pair = keys.generateKeyPair();
        pem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        pem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
        String checksum = UUID.randomUUID().toString();
        List<Process> owned = new ArrayList<>();
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("billing_packaged_restart").withUsername("billing").withPassword(UUID.randomUUID().toString());
             var redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
             var provider = new LoopbackPayosProvider(json, checksum)) {
            postgres.start();
            redis.start();
            int port;
            try (var available = new ServerSocket(0)) { port = available.getLocalPort(); }
            URI origin = URI.create("http://localhost:" + port);
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            try (var browser = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
                 var callbackClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
                Process first = boot(jar, postgres, redis, origin, provider.origin(), checksum, privateKey, publicKey, output.resolve("first.log"));
                owned.add(first);
                ready(first, callbackClient, origin);
                var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
                assertThat(jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success", Integer.class)).isEqualTo(40);
                billingSnapshot(callbackClient, origin, "PENDING_PAYMENT", 0, output.resolve("initial-metrics.prom"));
                UUID user = UUID.randomUUID();
                String password = UUID.randomUUID() + "!";
                String email = "restart-" + user + "@example.invalid";
                jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Packaged restart fixture','ACTIVE',now(),now())", user);
                jdbc.update("insert into user_identities(id,user_id,provider,email_normalized,password_hash,verified_at,created_at) values (?,?,'EMAIL',?,?,now(),now())",
                    UUID.randomUUID(), user, email, new PasswordConfiguration().passwordEncoder().encode(password));
                JsonNode csrf = json.readTree(send(browser, origin, "GET", "/api/v1/auth/csrf", null, null).body());
                var login = send(browser, origin, "POST", "/api/v1/auth/login", json.createObjectNode().put("email", email).put("password", password), csrf);
                assertThat(login.statusCode()).isEqualTo(200);
                var access = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("covie_at")).findFirst().orElseThrow();
                assertThat(access.isHttpOnly()).isTrue();
                byte[] issuedCookie = access.getValue().getBytes(StandardCharsets.UTF_8);
                csrf = json.readTree(send(browser, origin, "GET", "/api/v1/auth/csrf", null, null).body());
                var created = send(browser, origin, "POST", "/api/v1/me/merchants", json.createObjectNode().put("name", "Packaged restart shop"), csrf);
                assertThat(created.statusCode()).isEqualTo(201);
                UUID shop = UUID.fromString(json.readTree(created.body()).path("id").asText());
                UUID invoice = UUID.randomUUID();
                long orderCode = 4_000_000_000L;
                String link = String.format("%032x", orderCode);
                jdbc.update("""
                    insert into billing_invoices(id,invoice_number,merchant_id,plan_version_id,policy_version_id,status,
                        amount_vnd,period_start,period_end,pricing_snapshot,idempotency_key,request_fingerprint,
                        created_at,expires_at,payos_order_code,grace_until)
                    select ?, 'JVM-RESTART', ?, v.id,p.id,'PENDING_PAYMENT',10000,now(),now()+interval '1 month',
                        '{"priceVnd":10000,"planCode":"SELLER_STANDARD"}'::jsonb,?,repeat('a',64),now(),now()+interval '15 minutes',?,now()+interval '1 month 3 days'
                    from billing_plan_versions v cross join billing_policy_versions p
                    where v.plan_code='SELLER_STANDARD' and v.revision=1 and p.revision=1
                    """, invoice, shop, UUID.randomUUID().toString(), orderCode);
                jdbc.update("""
                    insert into billing_payment_attempts(id,invoice_id,provider,provider_order_id,status,expected_amount_vnd,
                        created_at,provider_payment_link_id,checkout_url,qr_payload)
                    values (?,?,'PAYOS',?,'PENDING',10000,now(),?,?,'fixture-no-transfer')
                    """, UUID.randomUUID(), invoice, Long.toString(orderCode), link, "https://pay.payos.vn/web/" + link);
                String statusPath = "/api/v1/merchants/" + shop + "/billing/invoices/" + invoice;
                invoiceStatus(browser, origin, statusPath, "PENDING_PAYMENT");
                billingSnapshot(callbackClient, origin, "PENDING_PAYMENT", 1, output.resolve("pending-metrics.prom"));
                Map<String, String> before = snapshot(jdbc);
                JsonNode callback = provider.signed(json.createObjectNode().put("orderCode", orderCode).put("amount", 10000)
                    .put("currency", "VND").put("paymentLinkId", link).put("reference", "owned-jvm-" + invoice).put("code", "00"));
                try (var blocker = jdbc.getDataSource().getConnection()) {
                    blocker.setAutoCommit(false);
                    try (var statement = blocker.createStatement()) { statement.execute("lock table outbox_events in share mode"); }
                    var delivery = callbackClient.sendAsync(request(origin, "POST", "/api/v1/billing/webhooks/payos", callback, null), HttpResponse.BodyHandlers.ofString());
                    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                    int waiting;
                    do {
                        waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where application_name='billing-packaged-restart' and wait_event_type='Lock' and lower(query) like '%insert into outbox_events%'", Integer.class);
                        if (waiting == 0) Thread.sleep(20);
                    } while (waiting == 0 && System.nanoTime() < deadline);
                    assertThat(waiting).as("actual signed settlement waits at final outbox INSERT").isEqualTo(1);
                    first.destroyForcibly();
                    assertThat(first.waitFor(15, TimeUnit.SECONDS)).isTrue();
                    assertThat(first.isAlive()).isFalse();
                    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> delivery.get(15, TimeUnit.SECONDS))).isNotNull();
                    blocker.rollback();
                }
                assertThat(postgres.isRunning()).isTrue();
                assertThat(redis.isRunning()).isTrue();
                long disconnectedDeadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                int connections;
                do {
                    connections = jdbc.queryForObject("select count(*) from pg_stat_activity where application_name='billing-packaged-restart'", Integer.class);
                    if (connections != 0) Thread.sleep(20);
                } while (connections != 0 && System.nanoTime() < disconnectedDeadline);
                assertThat(connections).as("killed application sessions are gone before checking rollback").isZero();
                assertThat(snapshot(jdbc)).isEqualTo(before);
                Process second = boot(jar, postgres, redis, origin, provider.origin(), checksum, privateKey, publicKey, output.resolve("second.log"));
                owned.add(second);
                assertThat(second.pid()).isNotEqualTo(first.pid());
                ready(second, callbackClient, origin);
                invoiceStatus(browser, origin, statusPath, "PENDING_PAYMENT");
                var retainedAccess = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("covie_at")).findFirst().orElseThrow();
                assertThat(MessageDigest.isEqual(issuedCookie, retainedAccess.getValue().getBytes(StandardCharsets.UTF_8))).isTrue();
                assertThat(snapshot(jdbc)).isEqualTo(before);
                var settled = send(callbackClient, origin, "POST", "/api/v1/billing/webhooks/payos", callback, null);
                assertThat(settled.statusCode()).isEqualTo(200);
                assertThat(json.readTree(settled.body()).path("status").asText()).isEqualTo("settled");
                invoiceStatus(browser, origin, statusPath, "PAID");
                assertThat(jdbc.queryForObject("select count(*) from billing_invoices where id=? and status='PAID'", Long.class, invoice)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=? and status='SUCCEEDED'", Long.class, invoice)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoice)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from merchant_subscriptions where merchant_id=? and status='ACTIVE'", Long.class, shop)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_webhook_inbox where processing_status='DONE'", Long.class)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'", Long.class, invoice)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'", Long.class, invoice)).isEqualTo(1L);
                Map<String, String> paid = snapshot(jdbc);
                var duplicate = send(callbackClient, origin, "POST", "/api/v1/billing/webhooks/payos", callback, null);
                assertThat(duplicate.statusCode()).isEqualTo(200);
                assertThat(json.readTree(duplicate.body()).path("status").asText()).isEqualTo("duplicate");
                assertThat(snapshot(jdbc)).isEqualTo(paid);
                String measured = billingSnapshot(callbackClient, origin, "PAID", 1, output.resolve("paid-metrics.prom"));
                assertThat(measured).containsPattern("covie_billing_operations_seconds_count\\{[^}]*operation=\"webhook\"[^}]*outcome=\"settled\"[^}]*} 1(?:\\.0)?(?:\\n|\\r)");
                assertThat(measured).doesNotContain(shop.toString(), invoice.toString());
                assertThat(provider.creations).hasValue(0);
                assertThat(provider.lookups).hasValue(0);
                provider.assertHealthy();
                Files.writeString(output.resolve("result.json"), json.writeValueAsString(Map.of(
                    "completed", true, "firstPid", first.pid(), "secondPid", second.pid(), "postgresContinuouslyRunning", true,
                    "observedOutboxLock", true, "rollbackSnapshotEqual", true, "originalHttpOnlySessionRetained", true,
                    "exactlyOnceSettlement", true, "providerRequests", 0, "jarSha256", sha(Files.readAllBytes(jar)))));
            } finally {
                for (Process process : owned) {
                    if (process.isAlive()) process.destroyForcibly();
                    assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
                }
            }
        } finally {
            Files.deleteIfExists(privateKey);
            Files.deleteIfExists(publicKey);
        }
    }

    private Process boot(Path jar, PostgreSQLContainer<?> postgres, GenericContainer<?> redis, URI origin,
                         String provider, String checksum, Path privateKey, Path publicKey, Path log) throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "-Duser.timezone=UTC", "-jar", jar.toString(), "--spring.profiles.active=local", "--server.port=" + origin.getPort(), "--server.address=127.0.0.1",
            "--covie.outbox.max-events-per-poll=0", "--covie.retention.enabled=false", "--covie.rate-limit.enabled=false",
            "--management.endpoints.web.exposure.include=health,info,metrics,prometheus", "--covie.billing.metrics-refresh-ms=1000");
        var environment = builder.environment();
        environment.keySet().removeIf(name -> name.startsWith("PAYOS_") || name.startsWith("COVIE_") || name.startsWith("SPRING_")
            || name.startsWith("DB_") || name.startsWith("AWS_") || name.startsWith("GOOGLE_OAUTH_")
            || List.of("REDIS_URL", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH").contains(name));
        environment.putAll(Map.of("DB_URL", postgres.getJdbcUrl() + "&ApplicationName=billing-packaged-restart",
            "DB_USERNAME", postgres.getUsername(), "DB_PASSWORD", postgres.getPassword(),
            "REDIS_URL", "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379),
            "COVIE_JWT_PRIVATE_KEY_PATH", privateKey.toString(), "COVIE_JWT_PUBLIC_KEY_PATH", publicKey.toString(),
            "PAYOS_CHECKSUM_KEY", checksum, "PAYOS_PAYMENT_ENABLED", "false", "COVIE_BILLING_RELEASE_APPROVED", "false",
            "COVIE_BILLING_RECONCILE_ENABLED", "false"));
        environment.put("COVIE_STORAGE_ENDPOINT", provider);
        environment.put("COVIE_MEDIA_PUBLIC_BASE_URL", provider + "/fixture-bucket");
        environment.put("AWS_ACCESS_KEY_ID", "owned-fixture-only");
        environment.put("AWS_SECRET_ACCESS_KEY", UUID.randomUUID().toString());
        environment.put("AWS_EC2_METADATA_DISABLED", "true");
        return builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private String billingSnapshot(HttpClient client, URI origin, String status, int count, Path evidence) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        String measured;
        do {
            var response = send(client, origin, "GET", "/actuator/prometheus", null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            measured = response.body();
            if (java.util.regex.Pattern.compile("covie_billing_invoices\\{status=\"" + status + "\"} " + count + "(?:\\.0)?(?:\\n|\\r)").matcher(measured).find()) break;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(measured).containsPattern("covie_billing_invoices\\{status=\"" + status + "\"} " + count + "(?:\\.0)?(?:\\n|\\r)");
        assertThat(measured).containsPattern("covie_billing_reconciliation_enabled 0(?:\\.0)?(?:\\n|\\r)")
            .containsPattern("covie_billing_payment_creation_enabled 0(?:\\.0)?(?:\\n|\\r)");
        Files.writeString(evidence, measured);
        return measured;
    }

    private void ready(Process process, HttpClient client, URI origin) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (process.isAlive() && System.nanoTime() < deadline) {
            try {
                if (send(client, origin, "GET", "/api/v1/health", null, null).statusCode() == 200) return;
            } catch (java.io.IOException ignored) { }
            Thread.sleep(100);
        }
        throw new AssertionError("Owned packaged API failed readiness; inspect its private fixture log");
    }

    private HttpRequest request(URI origin, String method, String path, JsonNode body, JsonNode csrf) {
        var builder = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(30));
        if (csrf != null) builder.header(csrf.path("headerName").asText(), csrf.path("token").asText());
        if (body == null) return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        return builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    private HttpResponse<String> send(HttpClient client, URI origin, String method, String path, JsonNode body, JsonNode csrf) throws Exception {
        return client.send(request(origin, method, path, body, csrf), HttpResponse.BodyHandlers.ofString());
    }

    private void invoiceStatus(HttpClient browser, URI origin, String path, String expected) throws Exception {
        var response = send(browser, origin, "GET", path, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(json.readTree(response.body()).path("invoiceStatus").asText()).isEqualTo(expected);
    }

    private Map<String, String> snapshot(JdbcTemplate jdbc) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String table : List.of("billing_invoices", "billing_payment_attempts", "billing_entitlement_grants", "merchant_subscriptions",
                "billing_webhook_inbox", "audit_logs", "outbox_events")) {
            rows.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(row) order by to_jsonb(row)::text),'[]'::jsonb)::text from " + table + " row", String.class));
        }
        return rows;
    }

    private void verifyArtifact(Path jar) throws Exception {
        assertThat(Files.isRegularFile(jar)).as("explicit packaged API artifact exists").isTrue();
        Path classes = Path.of("target/classes");
        try (var archive = new ZipFile(jar.toFile()); var source = Files.walk(classes)) {
            var compiled = source.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class"))
                .map(path -> classes.relativize(path).toString().replace('\\', '/')).collect(java.util.stream.Collectors.toSet());
            var packaged = archive.stream().map(java.util.zip.ZipEntry::getName)
                .filter(name -> name.startsWith("BOOT-INF/classes/") && name.endsWith(".class"))
                .map(name -> name.substring("BOOT-INF/classes/".length())).collect(java.util.stream.Collectors.toSet());
            assertThat(packaged).isEqualTo(compiled);
            var paths = new ArrayList<>(compiled);
            try (var resources = Files.walk(classes)) {
                resources.filter(Files::isRegularFile).map(classes::relativize).map(Path::toString)
                    .map(path -> path.replace('\\', '/')).filter(path -> path.startsWith("db/migration/") || path.matches("application.*\\.yml"))
                    .forEach(paths::add);
            }
            for (String path : paths) {
                var entry = archive.getEntry("BOOT-INF/classes/" + path);
                assertThat(entry).as("packaged current resource %s", path).isNotNull();
                try (var input = archive.getInputStream(entry)) {
                    assertThat(sha(input.readAllBytes())).as("packaged bytes %s", path).isEqualTo(sha(Files.readAllBytes(classes.resolve(path))));
                }
            }
        }
    }

    private static void pem(Path path, String label, byte[] bytes) throws Exception {
        Files.writeString(path, "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
            + "\n-----END " + label + "-----\n");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
