package vn.covie.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.billing.payos.PayosSignature;
import vn.covie.billing.support.LoopbackPayosProvider;
import vn.covie.billing.reconciliation.BillingReconciliationService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false",
    "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
    "PAYOS_PAYMENT_ENABLED=false", "COVIE_BILLING_RELEASE_APPROVED=false",
    "COVIE_BILLING_RECONCILE_ENABLED=false",
    "COVIE_BILLING_FINANCE_ROLE_ASSIGNMENT_APPROVED=true",
    "COVIE_BILLING_ADMIN_GRANTS_APPROVED=true",
    "COVIE_BILLING_LEGACY_GRANTS_APPROVED=true",
    "COVIE_BILLING_REFUND_CONFIRM_APPROVED=true",
    "covie.production.public-url=https://billing-fixture.invalid"
})
@ActiveProfiles("test")
@Import({BillingAuthorizationHttpIntegrationTest.ProviderConfiguration.class,
    vn.covie.billing.support.RequestSqlCounter.Configuration.class})
class BillingAuthorizationHttpIntegrationTest {
    private static final String CHECKSUM = "authorization-fixture-checksum";
    private static LoopbackPayosProvider ownedProvider;
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("billing_authorization_test").withUsername("billing")
        .withPassword("billing-test-only")
        .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
            new com.github.dockerjava.api.model.PortBinding(
                com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", databasePort()),
                com.github.dockerjava.api.model.ExposedPort.tcp(5432))));
    static { POSTGRES.start(); }
    private static int databasePort() {
        try (var available = new ServerSocket(0)) {
            return available.getLocalPort();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @AfterAll static void cleanup() { if (ownedProvider != null) ownedProvider.close(); POSTGRES.stop(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean LoopbackPayosProvider providerTransport(ObjectMapper mapper) throws Exception {
            ownedProvider = new LoopbackPayosProvider(mapper, CHECKSUM);
            return ownedProvider;
        }
        @Bean @Primary PayosGateway fixtureProvider(LoopbackPayosProvider transport, PayosSignature signatures) throws Exception {
            return transport.gateway(signatures);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired InitialSessionIssuer sessions;
    @Autowired LoopbackPayosProvider provider;
    @Autowired BillingReconciliationService reconciliation;
    @Autowired vn.covie.billing.application.BillingLifecycleScheduler lifecycle;
    @Autowired io.micrometer.core.instrument.MeterRegistry billingMetrics;
    @Autowired vn.covie.billing.observability.BillingDatabaseMetrics billingDatabaseMetrics;
    @Autowired vn.covie.billing.application.BillingEntitlementService billingEligibility;
    @Autowired vn.covie.outbox.application.OutboxProcessor outboxProcessor;
    @Autowired vn.covie.notifications.application.BillingNotificationHandler billingNotifications;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private record Actor(UUID id, String token) {}

    @BeforeEach void resetFixtureSettings() { clearFixtureEnforcement(); provider.creations.set(0); }
    @AfterEach void clearFixtureEnforcement() {
        jdbc.update("delete from system_settings where setting_key='billing.enforcement_enabled' and scope_type='GLOBAL'");
        jdbc.update("update system_settings set value_json='false'::jsonb where setting_key='billing.finance_roles_enabled' and scope_type='GLOBAL'");
        provider.assertHealthy();
    }

    private Actor actor() {
        UUID user = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Billing actor','ACTIVE',?,?)", user, now, now);
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'USER',?)", UUID.randomUUID(), user, now);
        return new Actor(user, sessions.issue(user, new DeviceMetadata("Billing test", "billing-http-test")).accessToken().value());
    }

    private UUID merchant(Actor owner) {
        UUID merchant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
            insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
            values (?,'Billing authorization',?,'ACTIVE','APPROVED',?,?,0)
            """, merchant, "billing-auth-" + merchant, now, now);
        member(merchant, owner, "OWNER");
        return merchant;
    }

    private void member(UUID merchant, Actor actor, String role) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
            insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version)
            values (?,?,?,?,'ACTIVE',?,?,0)
            """, UUID.randomUUID(), merchant, actor.id(), role, now, now);
    }

    private String base(UUID merchant) { return "/api/v1/merchants/" + merchant + "/billing"; }

    private HttpResponse<String> request(String method, String path, Actor actor, JsonNode body, String key) throws Exception {
        return requestWithHeaders(method, path, actor, body, key, Map.of());
    }

    private HttpResponse<String> requestWithHeaders(String method, String path, Actor actor, JsonNode body,
            String key, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (actor != null) request.header("Authorization", "Bearer " + actor.token());
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        headers.forEach(request::header);
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode quote(Actor owner, UUID merchant) throws Exception {
        return quote(owner, merchant, "SELLER_STANDARD");
    }

    private ObjectNode quote(Actor owner, UUID merchant, String planCode) throws Exception {
        var response = request("POST", base(merchant) + "/quote?planCode=" + planCode, owner, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode quote = json.readTree(response.body());
        return json.createObjectNode().put("planCode", planCode)
            .put("expectedAmountVnd", quote.path("amountDueVnd").asLong())
            .put("expectedPlanRevision", quote.path("planRevision").asInt())
            .put("quoteId", quote.path("quoteId").asText());
    }

    private JsonNode purchase(Actor owner, UUID merchant) throws Exception {
        var response = request("POST", base(merchant) + "/checkout", owner, quote(owner, merchant), "http-" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private void settle(JsonNode invoice) throws Exception {
        long code = invoice.path("orderCode").asLong();
        provider.markPaid(code);
        ObjectNode payload = provider.callback(code);
        var response = request("POST", "/api/v1/billing/webhooks/payos", null, payload, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("status").asText()).isEqualTo("settled");
    }

    private Map<String, String> financialSnapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : java.util.List.of("billing_invoices", "billing_payment_attempts", "billing_entitlement_grants",
                "merchant_subscriptions", "billing_quotes", "billing_finance_cases")) {
            result.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from "
                + table + " entry", String.class));
        }
        return result;
    }

    @Test void managerStaffAndForeignOwnerCannotReadOrMutateOwnerFinancesAndRevocationAppliesToExistingJwt() throws Exception {
        Actor owner = actor(), foreignOwner = actor();
        merchant(foreignOwner);
        UUID merchant = merchant(owner);
        var invoice = purchase(owner, merchant);
        settle(invoice);
        ObjectNode validQuote = quote(owner, merchant);
        Map<String, String> before = financialSnapshot();
        assertThat(request("GET", base(merchant) + "/invoices", null, null, null).statusCode()).isEqualTo(401);
        for (String role : java.util.List.of("MANAGER", "STAFF", "FOREIGN")) {
            Actor member = role.equals("FOREIGN") ? foreignOwner : actor();
            if (!role.equals("FOREIGN")) member(merchant, member, role);
            int denied = role.equals("FOREIGN") ? 404 : 403;
            String invoicePath = base(merchant) + "/invoices/" + invoice.path("invoiceId").asText();
            assertThat(request("GET", base(merchant) + "/invoices", member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("GET", invoicePath, member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("GET", base(merchant) + "/subscription", member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("POST", base(merchant) + "/quote?planCode=SELLER_STANDARD", member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("POST", base(merchant) + "/checkout", member, validQuote, "denied-" + UUID.randomUUID()).statusCode()).isEqualTo(denied);
            assertThat(request("POST", base(merchant) + "/cancel-renewal", member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("POST", base(merchant) + "/resume-renewal", member, null, null).statusCode()).isEqualTo(denied);
            assertThat(request("POST", invoicePath + "/finance-cases", member,
                json.createObjectNode().put("kind", "REFUND").put("reason", "Fixture refund request"), null).statusCode()).isEqualTo(denied);
            assertThat(request("GET", "/api/v1/admin/billing/invoices", member, null, null).statusCode()).isEqualTo(403);
            if (!role.equals("FOREIGN")) {
                assertThat(request("GET", base(merchant) + "/summary", member, null, null).statusCode()).isEqualTo(200);
                jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=? and user_id=?",
                    merchant, member.id());
                assertThat(request("GET", base(merchant) + "/summary", member, null, null).statusCode()).isEqualTo(404);
            } else {
                assertThat(request("GET", base(merchant) + "/summary", member, null, null).statusCode()).isEqualTo(404);
            }
        }
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(1);
    }

    @Test void oneOwnerKeepsTwoShopsInvoicesAndEntitlementsIsolatedWhenOneFixtureExpires() throws Exception {
        Actor owner = actor();
        UUID first = merchant(owner), second = merchant(owner);
        JsonNode firstInvoice = purchase(owner, first), secondInvoice = purchase(owner, second);
        settle(firstInvoice); settle(secondInvoice);
        String immutableFirst = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class,
            UUID.fromString(firstInvoice.path("invoiceId").asText()));
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
            insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at)
            values (?,'billing.enforcement_enabled','BOOLEAN','true'::jsonb,'GLOBAL',?,?,?)
            """, UUID.randomUUID(), owner.id(), now, now);
        jdbc.update("update billing_entitlement_grants set valid_from=now()-interval '40 days',valid_until=now()-interval '7 days' where merchant_id=?", first);
        jdbc.update("update merchant_subscriptions set period_start=now()-interval '40 days',period_end=now()-interval '7 days',grace_until=now()-interval '4 days',status='EXPIRED' where merchant_id=?", first);
        JsonNode firstState = json.readTree(request("GET", base(first) + "/summary", owner, null, null).body());
        JsonNode secondState = json.readTree(request("GET", base(second) + "/summary", owner, null, null).body());
        assertThat(firstState.path("enforced").asBoolean()).isTrue();
        assertThat(firstState.path("canSell").asBoolean()).isFalse();
        assertThat(secondState.path("canSell").asBoolean()).isTrue();
        for (var shop : Map.of(first, firstInvoice, second, secondInvoice).entrySet()) {
            var response = request("GET", base(shop.getKey()) + "/invoices", owner, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("cache-control")).contains("no-store");
            JsonNode invoices = json.readTree(response.body());
            assertThat(invoices.size()).isEqualTo(1);
            assertThat(invoices.get(0).path("invoiceId").asText()).isEqualTo(shop.getValue().path("invoiceId").asText());
        }
        assertThat(request("GET", base(first) + "/invoices/" + secondInvoice.path("invoiceId").asText(), owner, null, null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class,
            UUID.fromString(firstInvoice.path("invoiceId").asText()))).isEqualTo(immutableFirst);
    }

    @Test void actualOwnerHttpReplayUsesConsumedExpiredQuoteAndChangedBodyConflicts() throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        ObjectNode original = quote(owner, merchant), changed = quote(owner, merchant);
        String key = "http-replay-" + UUID.randomUUID();
        var initial = request("POST", base(merchant) + "/checkout", owner, original, key);
        assertThat(initial.statusCode()).isEqualTo(200);
        jdbc.update("update billing_quotes set created_at=created_at-interval '1 day',expires_at=expires_at-interval '1 day' where id=?",
            UUID.fromString(original.path("quoteId").asText()));
        var replay = request("POST", base(merchant) + "/checkout", owner, original, key);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("invoiceId")).isEqualTo(json.readTree(initial.body()).path("invoiceId"));
        var conflict = request("POST", base(merchant) + "/checkout", owner, changed, key);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("error").path("code").asText())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isEqualTo(1L);
        assertThat(provider.creations).hasValue(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentOwnerCheckoutWithSameKeyCreatesOnePaymentAndRejectsChangedBody(boolean changedBody) throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        ObjectNode first = quote(owner, merchant);
        ObjectNode second = changedBody ? quote(owner, merchant) : first.deepCopy();
        String key = "concurrent-checkout-" + UUID.randomUUID();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
             var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("select id from merchant_profiles where id=? for update")) {
                    statement.setObject(1, merchant);
                    statement.executeQuery().close();
                }
                var firstCall = workers.submit(() -> request("POST", base(merchant) + "/checkout", owner, first, key));
                var secondCall = workers.submit(() -> request("POST", base(merchant) + "/checkout", owner, second, key));
                long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%merchant_profiles%'", Integer.class);
                    if (waiting != 2) Thread.sleep(20);
                } while (waiting != 2 && System.nanoTime() < deadline);
                assertThat(waiting).as("both authenticated checkout reservations overlap on the same merchant lock").isEqualTo(2);
                blocker.commit();
                var firstResponse = firstCall.get(20, TimeUnit.SECONDS);
                var secondResponse = secondCall.get(20, TimeUnit.SECONDS);
                var responses = java.util.List.of(firstResponse, secondResponse);
                assertThat(responses.stream().filter(response -> response.statusCode() == 200).count()).isGreaterThanOrEqualTo(1);
                for (var response : responses) {
                    assertThat(response.statusCode()).isIn(200, 409);
                    if (response.statusCode() == 409) {
                        assertThat(json.readTree(response.body()).path("error").path("code").asText()).isEqualTo(
                            changedBody ? "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST" : "BILLING_PAYMENT_UNAVAILABLE");
                    }
                }
                if (changedBody) assertThat(responses.stream().filter(response -> response.statusCode() == 409).count()).isEqualTo(1);
                ObjectNode winnerBody = firstResponse.statusCode() == 200 ? first : second;
                var replay = request("POST", base(merchant) + "/checkout", owner, winnerBody, key);
                assertThat(replay.statusCode()).isEqualTo(200);
                JsonNode winner = json.readTree(responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow().body());
                assertSameCheckout(json.readTree(replay.body()), winner);
                if (changedBody) {
                    var rejected = request("POST", base(merchant) + "/checkout", owner, winnerBody == first ? second : first, key);
                    assertThat(rejected.statusCode()).isEqualTo(409);
                    assertThat(json.readTree(rejected.body()).path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
                }
                UUID invoiceId = UUID.fromString(winner.path("invoiceId").asText());
                assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=?", Long.class, invoiceId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_quotes where merchant_id=? and consumed_by_idempotency_key=?", Long.class, merchant, key)).isEqualTo(1L);
                assertThat(provider.creations).hasValue(1);
                settle(winner);
                assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoiceId)).isEqualTo(1L);
            } finally {
                blocker.rollback();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"amount", "revision", "quote"})
    void historicalCheckoutReplayRejectsContradictionsToPersistedInvoiceAndConsumedQuote(String changedField) throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        ObjectNode original = quote(owner, merchant), changedQuote = quote(owner, merchant);
        String key = "historical-checkout-" + UUID.randomUUID();
        var initial = request("POST", base(merchant) + "/checkout", owner, original, key);
        assertThat(initial.statusCode()).isEqualTo(200);
        JsonNode invoice = json.readTree(initial.body());
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        String historicalHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(original.path("planCode").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("update billing_invoices set request_fingerprint=? where id=?", historicalHash, invoiceId);
        Map<String, String> before = financialSnapshot();
        var unverifiable = request("POST", base(merchant) + "/checkout", owner, original, key);
        assertThat(unverifiable.statusCode()).isEqualTo(409);
        JsonNode conflict = json.readTree(unverifiable.body()).path("error");
        assertThat(conflict.path("code").asText()).isEqualTo("BILLING_PAYMENT_UNAVAILABLE");
        assertThat(conflict.path("details").path("invoiceId").asText()).isEqualTo(invoiceId.toString());
        String statusPath = base(merchant) + "/invoices/" + invoiceId;
        assertThat(conflict.path("details").path("statusPath").asText()).isEqualTo(statusPath);
        assertThat(financialSnapshot()).isEqualTo(before);
        ObjectNode changed = switch (changedField) {
            case "amount" -> original.deepCopy().put("expectedAmountVnd", original.path("expectedAmountVnd").asLong() + 1);
            case "revision" -> original.deepCopy().put("expectedPlanRevision", original.path("expectedPlanRevision").asInt() + 1);
            case "quote" -> changedQuote;
            default -> throw new IllegalArgumentException(changedField);
        };
        var rejected = request("POST", base(merchant) + "/checkout", owner, changed, key);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).path("error").path("code").asText()).isEqualTo("BILLING_PAYMENT_UNAVAILABLE");
        assertThat(financialSnapshot()).isEqualTo(before);
        jdbc.update("delete from billing_quotes where id=?", UUID.fromString(original.path("quoteId").asText()));
        var afterQuoteCleanup = financialSnapshot();
        var replay = request("POST", base(merchant) + "/checkout", owner, original, key);
        assertThat(replay.statusCode()).isEqualTo(409);
        assertThat(json.readTree(replay.body()).path("error").path("details").path("statusPath").asText()).isEqualTo(statusPath);
        var status = request("GET", statusPath, owner, null, null);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode oldPayment = json.readTree(status.body());
        assertThat(oldPayment.path("invoiceId").asText()).isEqualTo(invoiceId.toString());
        assertThat(oldPayment.path("amountVnd").asLong()).isEqualTo(invoice.path("amountVnd").asLong());
        assertThat(oldPayment.path("checkoutUrl").asText()).isEqualTo(invoice.path("checkoutUrl").asText());
        assertThat(oldPayment.path("invoiceStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(financialSnapshot()).isEqualTo(afterQuoteCleanup);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoiceId)).isZero();
        assertThat(provider.creations).hasValue(1);
    }

    private void assertSameCheckout(JsonNode actual, JsonNode expected) {
        ObjectNode actualFields = (ObjectNode) actual.deepCopy(), expectedFields = (ObjectNode) expected.deepCopy();
        actualFields.remove("expiresAt");
        expectedFields.remove("expiresAt");
        assertThat(actualFields).isEqualTo(expectedFields);
        assertThat(Duration.between(Instant.parse(actual.path("expiresAt").asText()),
            Instant.parse(expected.path("expiresAt").asText())).abs()).isLessThan(Duration.ofNanos(1000));
    }

    @Test void unavailableCreateResponseKeepsOneReservationAndSignedLookupRecoversWithoutAnotherPost() throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        ObjectNode body = quote(owner, merchant);
        String key = "lost-response-" + UUID.randomUUID();
        provider.loseNextCreateResponse.set(true);
        var failed = request("POST", base(merchant) + "/checkout", owner, body, key);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(json.readTree(failed.body()).path("error").path("code").asText())
            .isEqualTo("BILLING_PAYMENT_UNAVAILABLE");
        var invoices = json.readTree(request("GET", base(merchant) + "/invoices", owner, null, null).body());
        assertThat(invoices.size()).isEqualTo(1);
        UUID invoiceId = UUID.fromString(invoices.get(0).path("invoiceId").asText());
        var pendingRetry = request("POST", base(merchant) + "/checkout", owner, body, key);
        assertThat(pendingRetry.statusCode()).isEqualTo(409);
        assertThat(provider.creations).hasValue(1);
        var candidate = reconciliation.pending(100).stream().filter(value -> value.invoiceId().equals(invoiceId)).findFirst().orElseThrow();
        assertThat(candidate.linkId()).isNull();
        var pending = reconciliation.queryProvider(candidate);
        assertThat(pending.providerStatus()).isEqualTo("PENDING");
        assertThat(pending.discrepancy()).isEqualTo("NONE");
        reconciliation.record(pending);
        var restored = request("POST", base(merchant) + "/checkout", owner, body, key);
        assertThat(restored.statusCode()).isEqualTo(200);
        JsonNode restoredPayment = json.readTree(restored.body());
        assertThat(restoredPayment.path("invoiceId").asText()).isEqualTo(invoiceId.toString());
        assertThat(restoredPayment.path("checkoutUrl").asText()).startsWith("https://pay.payos.vn/web/");
        provider.markPaid(candidate.orderCode());
        var refreshedCandidate = reconciliation.pending(100).stream().filter(value -> value.invoiceId().equals(invoiceId)).findFirst().orElseThrow();
        var paid = reconciliation.queryProvider(refreshedCandidate);
        assertThat(paid.providerStatus()).isEqualTo("PAID");
        assertThat(paid.discrepancy()).isEqualTo("NONE");
        assertThat(json.readTree(request("POST", "/api/v1/billing/webhooks/payos", null,
            provider.callback(candidate.orderCode()), null).body()).path("status").asText()).isEqualTo("duplicate");
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=? and status='SUCCEEDED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(provider.creations).hasValue(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"billing_invoices", "outbox_events"})
    void databaseCrashDuringWebhookRollsBackAndRecoveredWorkerSettlesExactlyOnce(String interruptedTable) throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        JsonNode invoice = purchase(owner, merchant);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        long orderCode = invoice.path("orderCode").asLong();
        provider.markPaid(orderCode);
        ObjectNode callback = provider.callback(orderCode);
        Map<String, String> before = financialSnapshot();
        Long auditsBefore = jdbc.queryForObject("select count(*) from audit_logs", Long.class);
        Long outboxBefore = jdbc.queryForObject("select count(*) from outbox_events", Long.class);
        var docker = org.testcontainers.DockerClientFactory.instance().client();
        String containerId = POSTGRES.getContainerId();
        try (var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
             var blocker = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                 POSTGRES.getUsername(), POSTGRES.getPassword())) {
            blocker.setAutoCommit(false);
            if (interruptedTable.equals("outbox_events")) {
                try (var statement = blocker.createStatement()) {
                    statement.execute("lock table outbox_events in share mode");
                }
            } else {
                try (var statement = blocker.prepareStatement("select id from billing_invoices where id=? for update")) {
                    statement.setObject(1, invoiceId);
                    statement.executeQuery().close();
                }
            }
            var delivery = workers.submit(() -> request("POST", "/api/v1/billing/webhooks/payos", null, callback, null));
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            int waiting;
            do {
                waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?", Integer.class,
                    interruptedTable.equals("outbox_events") ? "%insert into outbox_events%" : "%billing_invoices%");
                if (waiting == 0) Thread.sleep(20);
            } while (waiting == 0 && System.nanoTime() < deadline);
            assertThat(waiting).as("verified webhook transaction waits on %s before database crash", interruptedTable).isEqualTo(1);
            try {
                docker.killContainerCmd(containerId).withSignal("KILL").exec();
                assertThat(docker.inspectContainerCmd(containerId).exec().getState().getRunning()).isFalse();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> {
                    try (var unavailable = java.sql.DriverManager.getConnection(
                            POSTGRES.getJdbcUrl() + "&connectTimeout=1", POSTGRES.getUsername(), POSTGRES.getPassword())) {
                        unavailable.createStatement().execute("select 1");
                    }
                })).isInstanceOf(java.sql.SQLException.class);
                assertThat(delivery.get(20, TimeUnit.SECONDS).statusCode()).isBetween(500, 599);
            } finally {
                docker.startContainerCmd(containerId).exec();
            }
        }
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(200))
            .ignoreExceptions().until(() -> {
                try (var recovered = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(), POSTGRES.getPassword());
                     var statement = recovered.createStatement();
                     var result = statement.executeQuery("select 1")) {
                    return result.next() && result.getInt(1) == 1;
                }
            });
        assertThat(docker.inspectContainerCmd(containerId).exec().getState().getRunning()).isTrue();
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(auditsBefore);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isEqualTo(outboxBefore);
        assertThat(jdbc.queryForObject("select count(*) from billing_webhook_inbox where provider_event_id=?", Long.class,
            "ref:" + callback.path("data").path("reference").asText())).isZero();
        int lookupsBefore = provider.lookups.get();
        var worker = new vn.covie.billing.reconciliation.BillingReconciliationScheduler(reconciliation);
        worker.run();
        assertThat(provider.lookups.get()).isGreaterThan(lookupsBefore);
        assertThat(jdbc.queryForObject("select status from billing_invoices where id=?", String.class, invoiceId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=? and status='SUCCEEDED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        Map<String, String> settled = financialSnapshot();
        worker.run();
        var late = request("POST", "/api/v1/billing/webhooks/payos", null, callback, null);
        assertThat(late.statusCode()).isEqualTo(200);
        assertThat(json.readTree(late.body()).path("status").asText()).isEqualTo("duplicate");
        assertThat(financialSnapshot()).isEqualTo(settled);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(provider.creations).hasValue(1);
    }

    @Test void expiredUnconsumedQuoteCannotReserveAndFreshQuoteCanReuseTheUncommittedKey() throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        ObjectNode expired = quote(owner, merchant);
        String key = "expired-quote-" + UUID.randomUUID();
        jdbc.update("update billing_quotes set created_at=created_at-interval '1 day',expires_at=expires_at-interval '1 day' where id=?",
            UUID.fromString(expired.path("quoteId").asText()));
        var rejected = request("POST", base(merchant) + "/checkout", owner, expired, key);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).path("error").path("code").asText()).isEqualTo("BILLING_PRICE_INVALID");
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isZero();
        assertThat(provider.creations).hasValue(0);
        var fresh = request("POST", base(merchant) + "/checkout", owner, quote(owner, merchant), key);
        assertThat(fresh.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isEqualTo(1L);
        assertThat(provider.creations).hasValue(1);
    }

    private Actor administrator() {
        Actor initial = actor();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',?)",
            UUID.randomUUID(), initial.id(), Timestamp.from(Instant.now()));
        return new Actor(initial.id(), sessions.issue(initial.id(),
            new DeviceMetadata("Billing price test", "billing-price-test")).accessToken().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "2027-01-28T17:30:00Z,2027-02-28T17:30:00Z,2027-03-01T00:30",
        "2027-01-29T17:30:00Z,2027-02-28T17:30:00Z,2027-03-01T00:30",
        "2027-01-30T17:30:00Z,2027-02-28T17:30:00Z,2027-03-01T00:30",
        "2027-01-31T17:30:00Z,2027-02-28T17:30:00Z,2027-03-01T00:30",
        "2028-01-31T17:30:00Z,2028-02-29T17:30:00Z,2028-03-01T00:30",
        "2027-01-30T18:30:00Z,2027-02-28T18:30:00Z,2027-03-01T01:30"
    })
    void persistedEarlyRenewalUsesUtcCalendarAndPreservesIctInstant(String anchorText, String endText, String ictEnd) throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        JsonNode first = purchase(owner, merchant);
        settle(first);
        UUID firstId = UUID.fromString(first.path("invoiceId").asText());
        String firstBefore = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, firstId);
        Instant template = Instant.parse(anchorText);
        int year = futureCalendarYear(java.time.Year.isLeap(template.atZone(java.time.ZoneOffset.UTC).getYear()));
        Instant anchor = template.atZone(java.time.ZoneOffset.UTC).withYear(year).toInstant();
        Instant expectedEnd = Instant.parse(endText).atZone(java.time.ZoneOffset.UTC).withYear(year).toInstant();
        fixtureTerm(merchant, anchor, anchor.plus(Duration.ofDays(3)), "ACTIVE");
        jdbc.update("update billing_entitlement_grants set valid_from=now()-interval '1 day' where merchant_id=?", merchant);
        jdbc.update("update merchant_subscriptions set period_start=now()-interval '1 day' where merchant_id=?", merchant);
        JsonNode renewal = purchase(owner, merchant);
        UUID renewed = UUID.fromString(renewal.path("invoiceId").asText());
        assertThat(jdbc.queryForObject("select period_start from billing_invoices where id=?", Timestamp.class, renewed).toInstant()).isEqualTo(anchor);
        assertThat(jdbc.queryForObject("select period_end from billing_invoices where id=?", Timestamp.class, renewed).toInstant()).isEqualTo(expectedEnd);
        settle(renewal);
        assertThat(jdbc.queryForObject("select valid_from from billing_entitlement_grants where source_id=?", Timestamp.class, renewed).toInstant()).isEqualTo(anchor);
        Instant storedEnd = jdbc.queryForObject("select valid_until from billing_entitlement_grants where source_id=?", Timestamp.class, renewed).toInstant();
        assertThat(storedEnd).isEqualTo(expectedEnd);
        assertThat(storedEnd.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime().toString())
            .isEqualTo(java.time.LocalDateTime.parse(ictEnd).withYear(year).toString());
        assertThat(jdbc.queryForObject("select period_end from merchant_subscriptions where merchant_id=?", Timestamp.class, merchant).toInstant()).isEqualTo(expectedEnd);
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, firstId)).isEqualTo(firstBefore);
        assertThat(provider.creations).hasValue(2);
    }

    @Test void lateRenewalStartsAtCurrentServerTimeInsteadOfExpiredPaidThrough() throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        JsonNode first = purchase(owner, merchant);
        settle(first);
        Instant expired = Instant.now().minus(Duration.ofDays(1));
        fixtureTerm(merchant, expired, expired, "EXPIRED");
        Instant before = Instant.now();
        JsonNode renewed = purchase(owner, merchant);
        Instant after = Instant.now();
        UUID invoice = UUID.fromString(renewed.path("invoiceId").asText());
        Instant start = jdbc.queryForObject("select period_start from billing_invoices where id=?", Timestamp.class, invoice).toInstant();
        assertThat(start).isBetween(before.minusMillis(1), after.plusMillis(1));
        assertThat(start).isAfter(expired);
        Instant expectedEnd = start.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant();
        assertThat(jdbc.queryForObject("select period_end from billing_invoices where id=?", Timestamp.class, invoice).toInstant()).isEqualTo(expectedEnd);
        settle(renewed);
        assertThat(jdbc.queryForObject("select period_start from merchant_subscriptions where merchant_id=?", Timestamp.class, merchant).toInstant()).isEqualTo(start);
        assertThat(jdbc.queryForObject("select period_end from merchant_subscriptions where merchant_id=?", Timestamp.class, merchant).toInstant()).isEqualTo(expectedEnd);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, invoice)).isEqualTo(1L);
    }

    private void publishFixtureTrial(Actor admin, String mode, int days) throws Exception {
        int revision = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        Instant lastEffective = jdbc.queryForObject("select max(effective_at) from billing_policy_versions", Timestamp.class).toInstant();
        Instant effective = (lastEffective.isAfter(Instant.now()) ? lastEffective : Instant.now()).plusSeconds(2);
        Duration scheduledDelay = Duration.between(Instant.now(), effective);
        assertThat(scheduledDelay).isLessThan(Duration.ofSeconds(90));
        var response = requestWithHeaders("POST", "/api/v1/admin/billing/policy", admin, json.createObjectNode()
            .put("onboardingMode", mode).put("trialDays", days).put("graceDays", 3)
            .put("effectiveAt", effective.toString()), null, Map.of("If-Match", "\"" + revision + "\""));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String id = json.readTree(response.body()).path("id").asText();
        long deadline = System.nanoTime() + scheduledDelay.plusSeconds(10).toNanos();
        while (true) {
            var current = request("GET", "/api/v1/admin/billing/policy", admin, null, null);
            assertThat(current.statusCode()).isEqualTo(200);
            if (json.readTree(current.body()).path("id").asText().equals(id)) return;
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private static int futureCalendarYear(boolean leap) {
        int year = java.time.Year.now(java.time.ZoneOffset.UTC).getValue() + 1;
        while (java.time.Year.isLeap(year) != leap) year++;
        return year;
    }

    private HttpResponse<String> registerShop(Actor owner, String name) throws Exception {
        return request("POST", "/api/v1/me/merchants", owner, json.createObjectNode().put("name", name), null);
    }

    private UUID registeredShop(Actor owner, String name) throws Exception {
        var created = registerShop(owner, name);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(created.body()).path("id").asText());
    }

    @Test void trialOffDoesNotConsumeEligibilityAndOnGrantsConfiguredDurationOncePerOwner() throws Exception {
        Actor admin = administrator(), owner = actor();
        enableFixtureBilling(owner);
        publishFixtureTrial(admin, "PAY_BEFORE_SELL", 0);
        UUID off = registeredShop(owner, "Trial disabled fixture");
        assertThat(jdbc.queryForObject("select count(*) from billing_trial_claims where owner_user_id=?", Long.class, owner.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, off)).isZero();
        publishFixtureTrial(admin, "TRIAL", 7);
        assertThat(jdbc.update("delete from system_settings where setting_key='billing.enforcement_enabled' and scope_type='GLOBAL'")).isEqualTo(1);
        UUID observed = registeredShop(owner, "Observe-only trial fixture");
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, observed)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_trial_claims where owner_user_id=?", Long.class, owner.id())).isZero();
        enableFixtureBilling(owner);
        UUID first = registeredShop(owner, "First eligible trial fixture");
        UUID second = registeredShop(owner, "Second ineligible trial fixture");
        assertThat(jdbc.queryForObject("select count(*) from billing_trial_claims where owner_user_id=?", Long.class, owner.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select merchant_id from billing_trial_claims where owner_user_id=?", UUID.class, owner.id())).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, second)).isZero();
        Timestamp start = jdbc.queryForObject("select period_start from merchant_subscriptions where merchant_id=?", Timestamp.class, first);
        Timestamp end = jdbc.queryForObject("select trial_end from merchant_subscriptions where merchant_id=?", Timestamp.class, first);
        assertThat(Duration.between(start.toInstant(), end.toInstant())).isEqualTo(Duration.ofDays(7));
        assertThat(jdbc.queryForObject("select status from merchant_subscriptions where merchant_id=?", String.class, first)).isEqualTo("TRIALING");
        assertThat(jdbc.queryForObject("select grace_until from merchant_subscriptions where merchant_id=?", Timestamp.class, first).toInstant()).isEqualTo(end.toInstant().plus(Duration.ofDays(3)));
        assertThat(json.readTree(request("GET", base(first) + "/summary", owner, null, null).body()).path("canSell").asBoolean()).isTrue();
        assertThat(json.readTree(request("GET", base(second) + "/summary", owner, null, null).body()).path("canSell").asBoolean()).isFalse();
        assertThat(provider.creations).hasValue(0);
    }

    @Test void concurrentShopRegistrationsForOneOwnerCannotDoubleClaimTrial() throws Exception {
        Actor admin = administrator(), owner = actor();
        enableFixtureBilling(owner);
        publishFixtureTrial(admin, "TRIAL", 7);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
             var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("select id from users where id=? for update")) {
                    statement.setObject(1, owner.id()); statement.executeQuery().close();
                }
                var first = workers.submit(() -> registerShop(owner, "Concurrent trial first"));
                var second = workers.submit(() -> registerShop(owner, "Concurrent trial second"));
                long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class);
                    if (waiting < 2) Thread.sleep(20);
                } while (waiting < 2 && System.nanoTime() < deadline);
                assertThat(waiting).as("both actual registration requests overlap at PostgreSQL owner row locks").isEqualTo(2);
                blocker.commit();
                var responses = java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(201, 201);
            } finally { blocker.rollback(); }
        }
        assertThat(jdbc.queryForObject("select count(*) from merchant_memberships where user_id=? and role='OWNER'", Long.class, owner.id())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from billing_trial_claims where owner_user_id=?", Long.class, owner.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants g join merchant_memberships m on m.merchant_profile_id=g.merchant_id where m.user_id=? and g.source_type='TRIAL'", Long.class, owner.id())).isEqualTo(1L);
        assertThat(provider.creations).hasValue(0);
    }

    @Test void adminDisablesOnlyNewShopRegistrationWithoutBlockingBuyersOrExistingMerchants() throws Exception {
        Actor admin = administrator(), owner = actor(), buyer = actor(), newcomer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        JsonNode invoice = purchase(owner, merchant);
        settle(invoice);
        enableFixtureBilling(owner);
        UUID oldOrder = buyerOrder(buyer, offer);
        String settingPath = "/api/v1/admin/settings/billing.seller_registration_enabled";
        var detail = request("GET", settingPath, admin, null, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode initial = json.readTree(detail.body());
        assertThat(initial.path("effectiveValue").asBoolean()).isTrue();
        Map<String, String> headers = !initial.hasNonNull("effectiveSource") ? Map.of()
            : Map.of("If-Match", "\"" + initial.path("effectiveSource").path("version").asLong() + "\"");
        ObjectNode toggle = json.createObjectNode().put("scopeType", "GLOBAL").put("value", false);
        var disabled = requestWithHeaders("PATCH", settingPath, admin, toggle, null, headers);
        assertThat(disabled.statusCode()).as(disabled.body()).isIn(200, 201);
        UUID setting = UUID.fromString(json.readTree(disabled.body()).path("id").asText());
        String revision = disabled.headers().firstValue("etag").orElseThrow();
        try {
            Map<String, String> finances = financialSnapshot();
            Map<String, String> commerce = commerceSnapshot();
            long shopCount = jdbc.queryForObject("select count(*) from merchant_profiles", Long.class);
            long membershipCount = jdbc.queryForObject("select count(*) from merchant_memberships", Long.class);
            for (Actor applicant : java.util.List.of(owner, newcomer)) {
                var denied = registerShop(applicant, "Disabled registration fixture");
                assertThat(denied.statusCode()).isEqualTo(409);
                assertThat(json.readTree(denied.body()).path("error").path("code").asText()).isEqualTo("MERCHANT_ACTION_NOT_ALLOWED");
            }
            assertThat(jdbc.queryForObject("select count(*) from merchant_profiles", Long.class)).isEqualTo(shopCount);
            assertThat(jdbc.queryForObject("select count(*) from merchant_memberships", Long.class)).isEqualTo(membershipCount);
            assertThat(financialSnapshot()).isEqualTo(finances);
            assertThat(commerceSnapshot()).isEqualTo(commerce);
            assertThat(request("GET", "/api/v1/me/merchants", owner, null, null).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/v1/merchants/" + merchant, owner, null, null).statusCode()).isEqualTo(200);
            JsonNode summary = json.readTree(request("GET", base(merchant) + "/summary", owner, null, null).body());
            assertThat(summary.path("canSell").asBoolean()).isTrue();
            assertThat(request("GET", "/api/v1/orders/" + oldOrder, buyer, null, null).statusCode()).isEqualTo(200);
            UUID newOrder = buyerOrder(buyer, offer);
            assertThat(newOrder).isNotEqualTo(oldOrder);
            assertThat(request("POST", "/api/v1/orders/" + oldOrder + "/confirm", owner, null,
                "registration-existing-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
            assertThat(financialSnapshot()).isEqualTo(finances);
            assertThat(provider.creations).hasValue(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='SYSTEM_SETTING_CHANGED'", Long.class, setting)).isEqualTo(1L);
        } finally {
            var enabled = requestWithHeaders("PATCH", settingPath, admin, toggle.put("value", true), null,
                Map.of("If-Match", revision));
            assertThat(enabled.statusCode()).as(enabled.body()).isEqualTo(200);
        }
        registeredShop(newcomer, "Registration restored fixture");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='SYSTEM_SETTING_CHANGED'", Long.class, setting)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from system_setting_history where setting_id=?", Long.class, setting)).isEqualTo(2L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"PAST_DUE,SEARCH", "PAST_DUE,SHOP", "EXPIRED,SEARCH", "EXPIRED,SHOP"})
    void publicDiscoveryCannotAdvertisePurchasableOffersWithoutCurrentBillingEntitlement(String status, String surface) throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        UUID item = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?", UUID.class, offer);
        UUID category = jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, item);
        String path = surface.equals("SEARCH")
            ? "/api/v1/catalog/items?lat=10.8&lng=106.7&categoryId=" + category
            : "/api/v1/catalog/merchants/" + merchant + "/items";
        var active = request("GET", path, null, null, null);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(json.readTree(active.body()).at("/page/items").findValuesAsText("id")).contains(item.toString());
        Instant end = Instant.now().minus(status.equals("PAST_DUE") ? Duration.ofSeconds(1) : Duration.ofDays(4))
            .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), status);
        JsonNode summary = json.readTree(request("GET", base(merchant) + "/summary", owner, null, null).body());
        assertThat(summary.path("canSell").asBoolean()).isFalse();
        assertThat(summary.path("reasonCode").asText()).isEqualTo(status.equals("PAST_DUE") ? "GRACE_PERIOD_READ_ONLY" : "SUBSCRIPTION_REQUIRED");
        Map<String, String> finances = financialSnapshot(), commerce = commerceSnapshot();
        assertThat(request("GET", "/api/v1/catalog/items/" + item + "?lat=10.8&lng=106.7", null, null, null).statusCode()).isEqualTo(404);
        var unavailable = request("GET", path, null, null, null);
        assertThat(unavailable.statusCode()).isEqualTo(200);
        assertThat(json.readTree(unavailable.body()).at("/page/items")).as(surface + " must agree with actual canSell=false in " + status).isEmpty();
        assertThat(financialSnapshot()).isEqualTo(finances);
        assertThat(commerceSnapshot()).isEqualTo(commerce);
        assertThat(provider.creations).hasValue(1);
    }

    @Test void buyerWithoutSubscriptionUsesSearchSocialCartAndReviewsAndExpiryPreservesHistory() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        UUID item = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?", UUID.class, offer);
        UUID category = jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, item);
        String search = "/api/v1/catalog/items?lat=10.8&lng=106.7&categoryId=" + category;
        Map<String, String> finances = financialSnapshot();
        for (Actor viewer : new Actor[]{null, buyer}) {
            var response = request("GET", search, viewer, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).at("/page/items").findValuesAsText("id")).contains(item.toString());
        }
        String social = "/api/v1/me/catalog/items/" + item;
        for (String reaction : new String[]{"likes", "saves"}) {
            assertThat(request("PUT", social + "/" + reaction, buyer, null, null).statusCode()).isEqualTo(200);
        }
        var comment = request("POST", social + "/comments", buyer,
            json.createObjectNode().put("body", "Buyer history remains free"), null);
        assertThat(comment.statusCode()).isEqualTo(201);
        String commentId = json.readTree(comment.body()).path("id").asText();
        var added = request("POST", "/api/v1/me/carts/items", buyer,
            json.createObjectNode().put("locationVariantId", offer.toString()).put("quantity", 1), null);
        assertThat(added.statusCode()).isEqualTo(201);
        ObjectNode cartCheckout = json.createObjectNode().put("fulfillmentMethod", "PICKUP").put("paymentMethod", "COD");
        cartCheckout.putObject("source").put("type", "CART").put("cartId", json.readTree(added.body()).path("id").asText());
        var preview = request("POST", "/api/v1/checkouts/preview", buyer, cartCheckout, null);
        assertThat(preview.statusCode()).isEqualTo(200);
        var committed = request("POST", "/api/v1/checkouts/commit", buyer, json.createObjectNode()
            .put("checkoutToken", json.readTree(preview.body()).path("checkoutToken").asText()), "free-buyer-" + UUID.randomUUID());
        assertThat(committed.statusCode()).isEqualTo(201);
        UUID order = UUID.fromString(json.readTree(committed.body()).path("id").asText());
        String orderPath = "/api/v1/orders/" + order;
        for (String action : new String[]{"confirm", "mark-ready-for-pickup"}) {
            assertThat(request("POST", orderPath + "/" + action, owner, null, "free-fulfill-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        }
        assertThat(request("POST", orderPath + "/confirm-received", buyer, null, "free-received-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        UUID orderItem = jdbc.queryForObject("select id from order_items where order_id=?", UUID.class, order);
        var review = request("POST", "/api/v1/me/reviews", buyer,
            json.createObjectNode().put("orderItemId", orderItem.toString()).put("rating", 5).put("comment", "Verified buyer history"),
            "free-review-" + UUID.randomUUID());
        assertThat(review.statusCode()).as(review.body()).isEqualTo(201);
        String reviewId = json.readTree(review.body()).path("id").asText();
        assertThat(financialSnapshot()).isEqualTo(finances);
        assertThat(jdbc.queryForObject("select count(*) from merchant_memberships where user_id=?", Long.class, buyer.id())).isZero();
        Map<String, String> history = socialAndReviewSnapshot();
        Instant end = Instant.now().minus(Duration.ofDays(4)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), "EXPIRED");
        assertThat(json.readTree(request("GET", base(merchant) + "/summary", owner, null, null).body()).path("canSell").asBoolean()).isFalse();
        assertThat(socialAndReviewSnapshot()).isEqualTo(history);
        String summaryPath = "/api/v1/catalog/social-summary?itemIds=" + item;
        JsonNode summary = json.readTree(request("GET", summaryPath, buyer, null, null).body()).path(item.toString());
        assertThat(summary.path("liked").asBoolean()).isTrue();
        assertThat(summary.path("saved").asBoolean()).isTrue();
        assertThat(summary.path("likes").asInt()).isEqualTo(1);
        assertThat(summary.path("comments").asInt()).isEqualTo(1);
        var comments = request("GET", "/api/v1/catalog/items/" + item + "/comments", null, null, null);
        assertThat(comments.statusCode()).isEqualTo(200);
        assertThat(json.readTree(comments.body()).path("items").findValuesAsText("id")).containsExactly(commentId);
        var reviews = request("GET", "/api/v1/catalog/items/" + item + "/reviews", null, null, null);
        assertThat(reviews.statusCode()).isEqualTo(200);
        assertThat(json.readTree(reviews.body()).path("items").findValuesAsText("id")).containsExactly(reviewId);
        assertThat(request("GET", orderPath, buyer, null, null).statusCode()).isEqualTo(200);
        assertThat(socialAndReviewSnapshot()).isEqualTo(history);
        assertThat(provider.creations).hasValue(1);
        jdbc.update("update catalog_social_comments set status='HIDDEN' where id=?", UUID.fromString(commentId));
        Map<String, String> moderated = socialAndReviewSnapshot();
        assertThat(json.readTree(request("GET", summaryPath, buyer, null, null).body()).path(item.toString()).path("comments").asInt()).isZero();
        assertThat(json.readTree(request("GET", "/api/v1/catalog/items/" + item + "/comments", null, null, null).body()).path("items")).isEmpty();
        assertThat(socialAndReviewSnapshot()).isEqualTo(moderated);
    }

    private Map<String, String> socialAndReviewSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : new String[]{"catalog_social_reactions", "catalog_social_comments", "reviews",
                "catalog_item_rating_stats", "merchant_rating_stats", "orders", "order_items", "catalog_items", "merchant_profiles"}) {
            snapshot.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from "
                + table + " entry", String.class));
        }
        return snapshot;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PAST_DUE", "EXPIRED"})
    void needResponsesRetainHistoryButCannotOfferCheckoutWithoutBillingEntitlement(String status) throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        UUID item = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?", UUID.class, offer);
        UUID category = jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, item);
        UUID location = jdbc.queryForObject("select merchant_location_id from location_variants where id=?", UUID.class, offer);
        var createdNeed = request("POST", "/api/v1/me/need-requests", buyer, json.createObjectNode()
            .put("type", "PRODUCT").put("categoryId", category.toString()).put("title", "Billing boundary request")
            .put("queryText", "Boundary item").put("requestedQuantity", 1).put("lat", 10.8).put("lng", 106.7)
            .put("radiusMeters", 5000).put("expiresAt", Instant.now().plusSeconds(3600).toString()), "billing-need-" + UUID.randomUUID());
        assertThat(createdNeed.statusCode()).as(createdNeed.body()).isEqualTo(201);
        String need = json.readTree(createdNeed.body()).path("id").asText();
        ObjectNode body = json.createObjectNode().put("merchantLocationId", location.toString());
        body.putArray("locationVariantIds").add(offer.toString());
        var createdResponse = request("POST", "/api/v1/need-requests/" + need + "/responses", owner, body, "billing-response-" + UUID.randomUUID());
        assertThat(createdResponse.statusCode()).as(createdResponse.body()).isEqualTo(201);
        String responseId = json.readTree(createdResponse.body()).path("id").asText();
        String readPath = "/api/v1/me/need-requests/" + need + "/responses";
        var active = request("GET", readPath, buyer, null, null);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(json.readTree(active.body()).at("/items/0/items/0/actionable").asBoolean()).isTrue();
        String originalNeed = jdbc.queryForObject("select to_jsonb(n)::text from need_requests n where id=?", String.class, UUID.fromString(need));
        String originalResponse = jdbc.queryForObject("select to_jsonb(r)::text from need_responses r where id=?", String.class, UUID.fromString(responseId));
        Instant end = Instant.now().minus(status.equals("PAST_DUE") ? Duration.ofSeconds(1) : Duration.ofDays(4))
            .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), status);
        Map<String, String> finances = financialSnapshot();
        var unavailable = request("GET", readPath, buyer, null, null);
        assertThat(unavailable.statusCode()).isEqualTo(200);
        JsonNode retained = json.readTree(unavailable.body()).at("/items/0");
        assertThat(retained.path("id").asText()).isEqualTo(responseId);
        assertThat(retained.at("/items/0/available").asBoolean()).as("Need offer agrees with current entitlement in " + status).isFalse();
        assertThat(retained.at("/items/0/actionable").asBoolean()).isFalse();
        assertBillingDenied(request("POST", "/api/v1/need-requests/" + need + "/responses", owner, body, "expired-response-" + UUID.randomUUID()));
        assertThat(jdbc.queryForObject("select to_jsonb(n)::text from need_requests n where id=?", String.class, UUID.fromString(need))).isEqualTo(originalNeed);
        assertThat(jdbc.queryForObject("select to_jsonb(r)::text from need_responses r where id=?", String.class, UUID.fromString(responseId))).isEqualTo(originalResponse);
        assertThat(financialSnapshot()).isEqualTo(finances);
    }

    @Test void publicAndNeedReadStatementCountsDoNotGrowPerReturnedItem() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        var offers = new java.util.ArrayList<UUID>();
        UUID category = null;
        for (int index = 0; index < 20; index++) {
            UUID offer = trackedOffer(merchant);
            UUID item = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?", UUID.class, offer);
            if (category == null) category = jdbc.queryForObject("select primary_category_id from catalog_items where id=?", UUID.class, item);
            else jdbc.update("update catalog_items set primary_category_id=? where id=?", category, item);
            offers.add(offer);
        }
        var createdNeed = request("POST", "/api/v1/me/need-requests", buyer, json.createObjectNode()
            .put("type", "PRODUCT").put("categoryId", category.toString()).put("title", "Measured billing request")
            .put("queryText", "Boundary item").put("requestedQuantity", 1).put("lat", 10.8).put("lng", 106.7)
            .put("radiusMeters", 5000).put("expiresAt", Instant.now().plusSeconds(3600).toString()), "measured-need-" + UUID.randomUUID());
        assertThat(createdNeed.statusCode()).as(createdNeed.body()).isEqualTo(201);
        String need = json.readTree(createdNeed.body()).path("id").asText();
        for (UUID offer : offers) {
            UUID location = jdbc.queryForObject("select merchant_location_id from location_variants where id=?", UUID.class, offer);
            ObjectNode response = json.createObjectNode().put("merchantLocationId", location.toString());
            response.putArray("locationVariantIds").add(offer.toString());
            var created = request("POST", "/api/v1/need-requests/" + need + "/responses", owner, response, "measured-response-" + UUID.randomUUID());
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        }
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String surface : new String[]{"SEARCH", "SHOP", "NEED"}) {
            String path = switch (surface) {
                case "SEARCH" -> "/api/v1/catalog/items?lat=10.8&lng=106.7&categoryId=" + category + "&limit=";
                case "SHOP" -> "/api/v1/catalog/merchants/" + merchant + "/items?limit=";
                default -> "/api/v1/me/need-requests/" + need + "/responses?limit=";
            };
            Actor viewer = surface.equals("NEED") ? buyer : null;
            String itemsPath = surface.equals("NEED") ? "/items" : "/page/items";
            int single = measuredReadStatements(path + "1", viewer, itemsPath, 1);
            int twenty = measuredReadStatements(path + "20", viewer, itemsPath, 20);
            System.out.println("BILLING_SQL_COUNT surface=" + surface + " single=" + single + " twenty=" + twenty);
            counts.put(surface, new int[]{single, twenty});
        }
        UUID firstItem = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?", UUID.class, offers.getFirst());
        String detail = "/api/v1/catalog/items/" + firstItem + "?lat=10.8&lng=106.7";
        int singleDetail = measuredReadStatements(detail, null, "/variants", 1);
        for (int index = 1; index < 20; index++) {
            UUID variant = UUID.randomUUID();
            jdbc.update("insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) values (?,? ,?,'Measured variant',false,'ACTIVE',?,now(),now(),0)",
                variant, firstItem, "MEASURED-" + index, index);
            jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at,version) select ?,merchant_location_id,?,price,currency_code,true,'UNTRACKED',0,now(),now(),0 from location_variants where id=?",
                UUID.randomUUID(), variant, offers.getFirst());
        }
        int twentyDetail = measuredReadStatements(detail, null, "/variants", 20);
        System.out.println("BILLING_SQL_COUNT surface=DETAIL single=" + singleDetail + " twenty=" + twentyDetail);
        counts.put("DETAIL", new int[]{singleDetail, twentyDetail});
        org.junit.jupiter.api.Assertions.assertAll(counts.entrySet().stream().map(entry ->
            (org.junit.jupiter.api.function.Executable) () -> assertThat(entry.getValue()[1])
                .as(entry.getKey() + " SQL execute count must not grow per returned item").isEqualTo(entry.getValue()[0])));
    }

    private int measuredReadStatements(String path, Actor actor, String itemsPath, int expectedItems) throws Exception {
        String requestId = UUID.randomUUID().toString();
        var response = requestWithHeaders("GET", path, actor, null, null,
            Map.of(vn.covie.billing.support.RequestSqlCounter.HEADER, requestId));
        int count = vn.covie.billing.support.RequestSqlCounter.take(requestId);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).at(itemsPath).size()).isEqualTo(expectedItems);
        assertThat(count).as("actual JDBC execution counter must be active").isPositive();
        return count;
    }

    private void enableFixtureFinanceRoles(Actor admin) throws Exception {
        String path = "/api/v1/admin/settings/billing.finance_roles_enabled";
        var current = request("GET", path, admin, null, null);
        assertThat(current.statusCode()).isEqualTo(200);
        JsonNode state = json.readTree(current.body());
        Map<String, String> headers = state.hasNonNull("effectiveSource")
            ? Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(
                state.path("effectiveSource").path("version").asLong())) : Map.of();
        var response = requestWithHeaders("PATCH", path, admin,
            json.createObjectNode().put("scopeType", "GLOBAL").put("value", true), null, headers);
        assertThat(response.statusCode()).as(response.body()).isIn(200, 201);
    }

    private void assignFinanceRole(Actor bootstrap, Actor target, String role) throws Exception {
        long before = jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_FINANCE_ROLE_ASSIGNED'", Long.class, target.id());
        var response = request("POST", "/api/v1/admin/billing/finance-access/" + target.id(), bootstrap,
            json.createObjectNode().put("role", role), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select finance_role from billing_finance_permissions where user_id=?", String.class, target.id())).isEqualTo(role);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_FINANCE_ROLE_ASSIGNED'", Long.class, target.id())).isEqualTo(before + 1);
    }

    @Test void adminManualReconciliationRecoversSignedPaymentOnceAndAuditsOperatorWithIdempotentReplay() throws Exception {
        Actor bootstrap = administrator(), operator = administrator(), reader = administrator(), owner = actor();
        UUID merchant = merchant(owner);
        JsonNode invoice = purchase(owner, merchant);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        provider.markPaid(invoice.path("orderCode").asLong());
        enableFixtureFinanceRoles(bootstrap);
        assignFinanceRole(bootstrap, operator, "OPERATOR");
        assignFinanceRole(bootstrap, reader, "READER");
        String path = "/api/v1/admin/billing/invoices/" + invoiceId + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Investigate missing callback in disposable fixture");
        String key = "manual-" + UUID.randomUUID();
        int before = provider.lookups.get();
        JsonNode readerInvoices = json.readTree(request("GET", "/api/v1/admin/billing/invoices?merchantId=" + merchant,
            reader, null, null).body());
        assertThat(readerInvoices.get(0).path("allowedActions").isArray()).isTrue();
        assertThat(readerInvoices.get(0).path("allowedActions")).isEmpty();
        JsonNode operatorInvoices = json.readTree(request("GET", "/api/v1/admin/billing/invoices?merchantId=" + merchant,
            operator, null, null).body());
        assertThat(operatorInvoices.get(0).path("allowedActions").toString()).isEqualTo("[\"RECONCILE\"]");
        assertThat(request("POST", path, reader, body, key).statusCode()).isEqualTo(403);
        assertThat(request("POST", path, owner, body, key).statusCode()).isEqualTo(403);
        assertThat(provider.lookups).hasValue(before);
        assertThat(request("POST", path, operator, body, null).statusCode()).isEqualTo(400);
        var result = request("POST", path, operator, body, key);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        assertThat(result.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode response = json.readTree(result.body());
        assertThat(response.path("invoiceStatus").asText()).isEqualTo("PAID");
        assertThat(response.path("providerStatus").asText()).isEqualTo("PAID");
        assertThat(response.path("discrepancy").asText()).isEqualTo("NONE");
        assertThat(response.path("operationId").asText()).isNotBlank();
        assertThat(response.path("checkedAt").asText()).isNotBlank();
        Map<String, String> after = financialSnapshot();
        var replay = request("POST", path, operator, body, key);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body())).isEqualTo(response);
        assertThat(request("POST", path, operator,
            body.deepCopy().put("reason", "Different investigation"), key).statusCode()).isEqualTo(409);
        assertThat(financialSnapshot()).isEqualTo(after);
        assertThat(provider.lookups).hasValue(before + 1);
        assertThat(provider.creations).hasValue(1);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_type='PAID_INVOICE' and source_id=?", Long.class, invoiceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action in ('BILLING_RECONCILIATION_REQUESTED','BILLING_RECONCILIATION_COMPLETED') and actor_user_id=?",
            Long.class, invoiceId, operator.id())).isEqualTo(2);
        var revoked = request("DELETE", "/api/v1/admin/billing/finance-access/" + operator.id(), bootstrap, null, null);
        assertThat(revoked.statusCode()).isEqualTo(204);
        assertThat(request("POST", path, operator, body, key).statusCode()).isEqualTo(403);
        assertThat(provider.lookups).hasValue(before + 1);
    }

    @Test void adminManualReconciliationDoesNotMutateTerminalInvoicesOrTrustUnsignedProviderData() throws Exception {
        Actor admin = administrator(), owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        String path = "/api/v1/admin/billing/invoices/" + invoiceId + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Terminal invoice verification fixture");
        provider.markPaid(invoice.path("orderCode").asLong());
        provider.tamperNextLookupSignature.set(true);
        var before = financialSnapshot();
        var unsigned = request("POST", path, admin, body, "unsigned-" + UUID.randomUUID());
        assertThat(unsigned.statusCode()).as(unsigned.body()).isEqualTo(200);
        assertThat(json.readTree(unsigned.body()).path("discrepancy").asText()).isEqualTo("PROVIDER_FAILURE");
        assertThat(financialSnapshot()).isEqualTo(before);
        settle(invoice);
        for (String status : java.util.List.of("PAID", "VOID", "REFUNDED", "DISPUTED")) {
            jdbc.update("update billing_invoices set status=? where id=?", status, invoiceId);
            before = financialSnapshot();
            var checked = request("POST", path, admin, body, "terminal-" + UUID.randomUUID());
            assertThat(checked.statusCode()).as(checked.body()).isEqualTo(200);
            JsonNode response = json.readTree(checked.body());
            assertThat(response.path("invoiceStatus").asText()).isEqualTo(status);
            assertThat(response.path("discrepancy").asText()).isEqualTo(status.equals("PAID") ? "NONE" : "MANUAL_REVIEW_REQUIRED");
            assertThat(financialSnapshot()).isEqualTo(before);
        }
    }

    @Test void adminManualReconciliationReleasesDatabaseTransactionDuringProviderIoAndRejectsConcurrentReplay() throws Exception {
        Actor admin = administrator(), owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        String path = "/api/v1/admin/billing/invoices/" + invoice.path("invoiceId").asText() + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Concurrent provider lookup fixture");
        String key = "concurrent-manual-" + UUID.randomUUID();
        int lookupsBefore = provider.lookups.get();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        provider.nextLookupAction.set(() -> {
            entered.countDown();
            try { assertThat(release.await(8, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> request("POST", path, admin, body, key));
            try {
                assertThat(entered.await(8, TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("""
                    select count(*) from pg_stat_activity where datname=current_database()
                    and pid<>pg_backend_pid() and state='idle in transaction'
                    """, Long.class)).as("provider I/O must not retain a database transaction").isZero();
                var duplicate = request("POST", path, admin, body, key);
                assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(409);
                assertThat(provider.lookups).hasValue(lookupsBefore + 1);
            } finally { release.countDown(); }
            var result = first.get(10, TimeUnit.SECONDS);
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
            var replay = request("POST", path, admin, body, key);
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(result.body()));
            assertThat(provider.lookups).hasValue(lookupsBefore + 1);
        } finally { provider.nextLookupAction.set(null); release.countDown(); }
    }

    @Test void adminManualReconciliationFailsClosedOnAuditFailureAndCanResumeExpiredClaim() throws Exception {
        Actor admin = administrator(), owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        String path = "/api/v1/admin/billing/invoices/" + invoiceId + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Audit rollback and interrupted lookup fixture");
        String key = "audit-manual-" + UUID.randomUUID();
        int before = provider.lookups.get();
        var financialBefore = financialSnapshot();
        for (String action : java.util.List.of("BILLING_RECONCILIATION_REQUESTED", "BILLING_RECONCILIATION_COMPLETED")) {
            jdbc.execute("""
                create function fixture_reject_reconcile_audit() returns trigger language plpgsql as $$
                begin if new.action='%s' then raise exception 'Fixture reconciliation audit failure'; end if; return new; end; $$;
                create trigger fixture_reject_reconcile_audit before insert on audit_logs for each row execute function fixture_reject_reconcile_audit();
                """.formatted(action));
            try {
                var failure = request("POST", path, admin, body, key);
                assertThat(failure.statusCode()).as(failure.body()).isEqualTo(500);
                assertThat(financialSnapshot()).isEqualTo(financialBefore);
                assertThat(provider.lookups).hasValue(before + (action.endsWith("REQUESTED") ? 0 : 1));
            } finally {
                jdbc.execute("drop trigger fixture_reject_reconcile_audit on audit_logs; drop function fixture_reject_reconcile_audit()");
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_RECONCILIATION_COMPLETED'", Long.class, invoiceId)).isZero();
        jdbc.update("""
            update idempotency_records set response_body=jsonb_set(response_body,'{leaseUntil}',to_jsonb(?::text))
            where actor_id=? and operation=? and status='IN_PROGRESS'
            """, Instant.now().minusSeconds(1).toString(), admin.id(), "ADMIN_BILLING_RECONCILIATION:" + invoiceId);
        var resumed = request("POST", path, admin, body, key);
        assertThat(resumed.statusCode()).as(resumed.body()).isEqualTo(200);
        assertThat(provider.lookups).hasValue(before + 2);
        assertThat(financialSnapshot()).isEqualTo(financialBefore);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_RECONCILIATION_COMPLETED'", Long.class, invoiceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select after_snapshot->>'reason' from audit_logs where entity_id=? and action='BILLING_RECONCILIATION_REQUESTED' order by created_at limit 1", String.class, invoiceId))
            .isEqualTo(body.path("reason").asText());
    }

    @Test void adminManualReconciliationRejectsStaleCompletionAfterAnExpiredLeaseWasReclaimed() throws Exception {
        Actor admin = administrator(), owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        String invoiceId = invoice.path("invoiceId").asText();
        String path = "/api/v1/admin/billing/invoices/" + invoiceId + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Interrupted operation takeover fixture");
        String key = "reclaimed-manual-" + UUID.randomUUID();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        provider.nextLookupAction.set(() -> {
            entered.countDown();
            try { assertThat(release.await(8, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var stale = executor.submit(() -> request("POST", path, admin, body, key));
            HttpResponse<String> current;
            try {
                assertThat(entered.await(8, TimeUnit.SECONDS)).isTrue();
                jdbc.update("""
                    update idempotency_records set response_body=jsonb_set(response_body,'{leaseUntil}',to_jsonb(?::text))
                    where actor_id=? and operation=? and status='IN_PROGRESS'
                    """, Instant.now().minusSeconds(1).toString(), admin.id(), "ADMIN_BILLING_RECONCILIATION:" + invoiceId);
                current = request("POST", path, admin, body, key);
                assertThat(current.statusCode()).as(current.body()).isEqualTo(200);
            } finally { release.countDown(); }
            var old = stale.get(10, TimeUnit.SECONDS);
            assertThat(old.statusCode()).as(old.body()).isEqualTo(409);
            var replay = request("POST", path, admin, body, key);
            assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(current.body()));
        } finally { provider.nextLookupAction.set(null); release.countDown(); }
    }

    @Test void adminManualReconciliationRequiresZeroReceivedFundsBeforeReportingVoidAsConsistent() throws Exception {
        Actor admin = administrator(), owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        jdbc.update("update billing_invoices set status='VOID' where id=?", invoiceId);
        var before = financialSnapshot();
        String path = "/api/v1/admin/billing/invoices/" + invoiceId + "/reconciliation";
        ObjectNode body = json.createObjectNode().put("reason", "Investigate provider-final partial payment fixture");
        try {
            for (String status : java.util.List.of("EXPIRED", "CANCELLED")) {
                for (int amountPaid : new int[] {5000, -1, 0}) {
                    provider.nextLookupData.set(data -> {
                        data.put("status", status);
                        if (amountPaid < 0) data.remove("amountPaid"); else data.put("amountPaid", amountPaid);
                    });
                    var result = request("POST", path, admin, body, "final-funds-" + UUID.randomUUID());
                    assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
                    assertThat(json.readTree(result.body()).path("discrepancy").asText())
                        .as("%s amountPaid=%s", status, amountPaid)
                        .isEqualTo(amountPaid == 0 ? "NONE" : "MANUAL_REVIEW_REQUIRED");
                    assertThat(financialSnapshot()).isEqualTo(before);
                }
            }
        } finally { provider.nextLookupData.set(null); }
    }

    @Test void legacyMigrationReplaysWithoutChangingGrantsAndShadowPreviewNeverBlocksExistingTraffic() throws Exception {
        Actor bootstrap = administrator(), reader = administrator(), approver = administrator(), owner = actor(), buyer = actor();
        enableFixtureFinanceRoles(bootstrap);
        assignFinanceRole(bootstrap, reader, "READER");
        assignFinanceRole(bootstrap, approver, "APPROVER");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant cutoff = now.minus(Duration.ofDays(10)), deadline = now.plus(Duration.ofDays(7));
        UUID[] cohort = new UUID[5];
        for (int index = 0; index < cohort.length; index++) {
            cohort[index] = merchant(owner);
            jdbc.update("update merchant_profiles set created_at=? where id=?", Timestamp.from(now.minus(Duration.ofDays(20))), cohort[index]);
        }
        UUID deleted = merchant(owner), newer = merchant(owner);
        jdbc.update("update merchant_profiles set created_at=?,deleted_at=? where id=?",
            Timestamp.from(now.minus(Duration.ofDays(20))), Timestamp.from(now), deleted);
        for (int index = 0; index < 4; index++) {
            Instant start = index == 1 ? now.plus(Duration.ofDays(1)) : now.minus(Duration.ofDays(3));
            Instant end = index == 2 ? now.minus(Duration.ofDays(1)) : now.plus(Duration.ofDays(3));
            jdbc.update("""
                insert into billing_entitlement_grants(id,merchant_id,source_type,source_id,valid_from,valid_until,
                    revoked_at,reason,created_at) values (?,?,'ADMIN',?,?,?,?, 'Explicit shadow grant state fixture',?)
                """, UUID.randomUUID(), cohort[index], UUID.randomUUID(), Timestamp.from(start), Timestamp.from(end),
                index == 3 ? Timestamp.from(now) : null, Timestamp.from(now));
        }
        UUID offer = trackedOffer(cohort[4]);
        buyerOrder(buyer, offer);
        ObjectNode command = json.createObjectNode().put("createdBefore", cutoff.toString())
            .put("validUntil", deadline.toString()).put("reason", "Disposable explicitly scoped legacy migration acceptance");
        String previewPath = "/api/v1/admin/billing/legacy/preview?createdBefore=" + cutoff;
        String grantPath = "/api/v1/admin/billing/legacy/grant";
        assertThat(request("POST", grantPath, reader, command, null).statusCode()).isEqualTo(403);
        Map<String, String> financialBefore = financialSnapshot(), commerceBefore = commerceSnapshot();
        String merchantsBefore = jdbc.queryForObject("select jsonb_agg(to_jsonb(m) order by id)::text from merchant_profiles m", String.class);
        String auditsBefore = jdbc.queryForObject("select jsonb_agg(to_jsonb(a) order by id)::text from audit_logs a", String.class);
        double allowedBefore = billingMetrics.counter("covie.billing.shadow.decisions", "decision", "ALLOW").count();
        double deniedBefore = billingMetrics.counter("covie.billing.shadow.decisions", "decision", "DENY").count();
        var preview = request("GET", previewPath, reader, null, null);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        JsonNode snapshot = json.readTree(preview.body());
        assertThat(snapshot.path("eligibleMerchants").asInt()).isEqualTo(5);
        assertThat(snapshot.path("previouslyGranted").asInt()).isZero();
        assertThat(snapshot.path("shadow").path("evaluatedMerchants").asInt()).isEqualTo(5);
        assertThat(snapshot.path("shadow").path("currentlyAllowed").asInt()).isEqualTo(5);
        assertThat(snapshot.path("shadow").path("wouldAllow").asInt()).isEqualTo(1);
        assertThat(snapshot.path("shadow").path("wouldDeny").asInt()).isEqualTo(4);
        assertThat(financialSnapshot()).isEqualTo(financialBefore);
        assertThat(commerceSnapshot()).isEqualTo(commerceBefore);
        assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(m) order by id)::text from merchant_profiles m", String.class)).isEqualTo(merchantsBefore);
        assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(a) order by id)::text from audit_logs a", String.class)).isEqualTo(auditsBefore);
        String nonLegacyBefore = jdbc.queryForObject("select jsonb_agg(to_jsonb(g) order by id)::text from billing_entitlement_grants g where source_type<>'LEGACY'", String.class);
        var first = request("POST", grantPath, approver, command, null);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(json.readTree(first.body()).path("newlyGranted").asInt()).isEqualTo(5);
        Map<String, String> granted = financialSnapshot();
        for (var entry : financialBefore.entrySet()) {
            if (!entry.getKey().equals("billing_entitlement_grants"))
                assertThat(granted.get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        }
        var replay = request("POST", grantPath, approver, command, null);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("newlyGranted").asInt()).isZero();
        assertThat(financialSnapshot()).isEqualTo(granted);
        assertThat(commerceSnapshot()).isEqualTo(commerceBefore);
        assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(g) order by id)::text from billing_entitlement_grants g where source_type<>'LEGACY'", String.class)).isEqualTo(nonLegacyBefore);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_type='LEGACY' and merchant_id in (?,?)", Long.class, deleted, newer)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='BILLING_LEGACY_ENTITLEMENTS_GRANTED' and actor_user_id=?", Long.class, approver.id())).isEqualTo(2);
        var grantAudits = jdbc.query("select after_snapshot::text from audit_logs where action='BILLING_LEGACY_ENTITLEMENTS_GRANTED' and actor_user_id=?",
            (result, row) -> result.getString(1), approver.id());
        var createdCounts = new java.util.ArrayList<Integer>();
        for (String stored : grantAudits) {
            JsonNode metadata = json.readTree(stored);
            assertThat(metadata.path("cutoff").asText()).isEqualTo(cutoff.toString());
            assertThat(metadata.path("validUntil").asText()).isEqualTo(deadline.toString());
            assertThat(metadata.path("reason").asText()).isEqualTo(command.path("reason").asText());
            createdCounts.add(metadata.path("created").asInt());
        }
        assertThat(createdCounts).containsExactlyInAnyOrder(5, 0);
        var after = request("GET", previewPath, reader, null, null);
        assertThat(after.statusCode()).as(after.body()).isEqualTo(200);
        JsonNode afterSnapshot = json.readTree(after.body());
        assertThat(afterSnapshot.path("previouslyGranted").asInt()).isEqualTo(5);
        assertThat(afterSnapshot.path("shadow").path("wouldAllow").asInt()).isEqualTo(5);
        assertThat(afterSnapshot.path("shadow").path("wouldDeny").asInt()).isZero();
        assertThat(financialSnapshot()).isEqualTo(granted);
        assertThat(commerceSnapshot()).isEqualTo(commerceBefore);
        assertThat(billingMetrics.counter("covie.billing.shadow.decisions", "decision", "ALLOW").count()).isEqualTo(allowedBefore + 6);
        assertThat(billingMetrics.counter("covie.billing.shadow.decisions", "decision", "DENY").count()).isEqualTo(deniedBefore + 4);
        buyerOrder(buyer, offer);
        assertThat(financialSnapshot()).isEqualTo(granted);
        assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(m) order by id)::text from merchant_profiles m", String.class)).isEqualTo(merchantsBefore);
        assertThat(provider.creations).hasValue(0);
        var expiredDeadline = request("POST", grantPath, approver,
            command.deepCopy().put("validUntil", now.minusSeconds(1).toString()), null);
        assertThat(expiredDeadline.statusCode()).isEqualTo(422);
        var futureCutoff = request("POST", grantPath, approver,
            command.deepCopy().put("createdBefore", now.plusSeconds(86400).toString()), null);
        assertThat(futureCutoff.statusCode()).isEqualTo(422);
        enableFixtureBilling(owner);
        assertThat(request("POST", grantPath, approver, command, null).statusCode()).isEqualTo(422);
        var enforced = request("GET", previewPath, reader, null, null);
        assertThat(enforced.statusCode()).as(enforced.body()).isEqualTo(200);
        JsonNode enforcedShadow = json.readTree(enforced.body()).path("shadow");
        assertThat(enforcedShadow.path("enforcementEnabled").asBoolean()).isTrue();
        assertThat(enforcedShadow.path("currentlyAllowed").asInt()).isEqualTo(5);
        assertThat(enforcedShadow.path("wouldDeny").asInt()).isZero();
        assertThat(financialSnapshot()).isEqualTo(granted);
    }

    private ObjectNode grantRequest() {
        return json.createObjectNode().put("validUntil", Instant.now().plus(Duration.ofDays(3)).toString())
            .put("reason", "Disposable local non-monetary grant fixture");
    }

    @Test void sellerStatusPollingAndCallbackKeepCounterpartyDataAndForeignInvoicesPrivate() throws Exception {
        Actor owner = actor(), foreignOwner = actor();
        UUID merchant = merchant(owner), foreignMerchant = merchant(foreignOwner);
        JsonNode invoice = purchase(owner, merchant), foreignInvoice = purchase(foreignOwner, foreignMerchant);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        String statusPath = base(merchant) + "/invoices/" + invoiceId;
        Map<String, String> before = financialSnapshot();
        int lookupsBefore = provider.lookups.get();
        for (int poll = 0; poll < 3; poll++) {
            var response = request("GET", statusPath, owner, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            JsonNode state = json.readTree(response.body());
            var fields = new java.util.HashSet<String>();
            state.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("invoiceId", "invoiceStatus", "paymentStatus", "amountVnd",
                "periodStart", "periodEnd", "checkoutUrl", "expiresAt");
            assertThat(state.path("invoiceId").asText()).isEqualTo(invoiceId.toString());
            assertThat(response.body()).doesNotContain(foreignInvoice.path("invoiceId").asText(),
                foreignMerchant.toString(), CHECKSUM, "loopback-api-only", "loopback-client-only");
        }
        assertThat(request("GET", statusPath, null, null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", statusPath, foreignOwner, null, null).statusCode()).isEqualTo(404);
        var foreignRead = request("GET", base(merchant) + "/invoices/" + foreignInvoice.path("invoiceId").asText(), owner, null, null);
        assertThat(foreignRead.statusCode()).isEqualTo(404);
        assertThat(foreignRead.body()).doesNotContain(foreignInvoice.path("checkoutUrl").asText(), foreignMerchant.toString());
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(provider.lookups).hasValue(lookupsBefore);
        long orderCode = invoice.path("orderCode").asLong();
        provider.markPaid(orderCode);
        ObjectNode data = (ObjectNode) provider.callback(orderCode).path("data");
        String privateAccount = "private-bank-" + UUID.randomUUID(), privateName = "private-counterparty-" + UUID.randomUUID();
        data.put("accountNumber", privateAccount).put("counterAccountName", privateName);
        ObjectNode callback = provider.signed(data);
        var forged = request("POST", "/api/v1/billing/webhooks/payos", null, callback.deepCopy().put("signature", "0".repeat(64)), null);
        assertThat(forged.statusCode()).isEqualTo(401);
        assertThat(forged.body()).doesNotContain(privateAccount, privateName, CHECKSUM, callback.path("signature").asText());
        assertThat(financialSnapshot()).isEqualTo(before);
        var accepted = request("POST", "/api/v1/billing/webhooks/payos", null, callback, null);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(accepted.body())).isEqualTo(json.createObjectNode().put("status", "settled"));
        var duplicate = request("POST", "/api/v1/billing/webhooks/payos", null, callback, null);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(json.readTree(duplicate.body())).isEqualTo(json.createObjectNode().put("status", "duplicate"));
        for (String table : java.util.List.of("billing_webhook_inbox", "audit_logs", "outbox_events")) {
            String stored = jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry)),'[]'::jsonb)::text from " + table + " entry", String.class);
            assertThat(stored).doesNotContain(privateAccount, privateName, CHECKSUM, callback.path("signature").asText());
        }
        assertThat(provider.creations).hasValue(2);
        assertThat(provider.lookups).hasValue(lookupsBefore);
    }

    @Test void adminInvoiceCsvExportIsBoundedScopedAndPrivate() throws Exception {
        Actor bootstrap = administrator(), reader = administrator(), owner = actor(), foreignOwner = actor();
        UUID merchant = merchant(owner), foreignMerchant = merchant(foreignOwner);
        JsonNode invoice = purchase(owner, merchant), foreignInvoice = purchase(foreignOwner, foreignMerchant);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        settle(invoice);
        String privateName = "private-owner-" + UUID.randomUUID();
        String privateNote = "private-finance-" + UUID.randomUUID();
        jdbc.update("update users set display_name=? where id=?", privateName, owner.id());
        jdbc.update("update billing_invoices set invoice_number=? where id=?", "=CSV,\"fixture\"", invoiceId);
        jdbc.update("insert into billing_finance_cases(id,invoice_id,merchant_id,kind,status,reason,created_by_user_id,created_at) values (?,?,?,'REFUND','REQUESTED',?,?,now())",
            UUID.randomUUID(), invoiceId, merchant, privateNote, owner.id());
        enableFixtureFinanceRoles(bootstrap);
        assignFinanceRole(bootstrap, reader, "READER");
        String exportPath = "/api/v1/admin/billing/invoices/export?merchantId=" + merchant + "&limit=20&offset=0";
        Map<String, String> before = financialSnapshot();
        String auditBefore = jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from audit_logs entry", String.class);
        String outboxBefore = jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from outbox_events entry", String.class);
        var exported = request("GET", exportPath, reader, null, null);
        assertThat(exported.statusCode()).isEqualTo(200);
        assertThat(exported.headers().firstValue("Content-Type").orElse("")).contains("text/csv").containsIgnoringCase("charset=UTF-8");
        assertThat(exported.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(exported.headers().firstValue("Content-Disposition").orElse("")).isEqualTo("attachment; filename=\"seller-billing-invoices.csv\"");
        assertThat(exported.body()).startsWith("invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n")
            .contains(invoiceId.toString(), merchant.toString(), "\"'=CSV,\"\"fixture\"\"\"", "\"PAID\"", "\"10000\"")
            .doesNotContain(foreignInvoice.path("invoiceId").asText(), foreignMerchant.toString(),
                privateName, privateNote, CHECKSUM, "loopback-api-only", "loopback-client-only",
                invoice.path("checkoutUrl").asText(), invoice.path("qrPayload").asText(), "allowedActions");
        assertThat(exported.body().lines().count()).isEqualTo(2);
        assertThat(request("GET", exportPath, null, null, null).statusCode()).isEqualTo(401);
        for (Actor denied : java.util.List.of(bootstrap, owner, foreignOwner)) {
            var forbidden = request("GET", exportPath, denied, null, null);
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.body()).doesNotContain(invoiceId.toString(), merchant.toString(), privateName, privateNote);
        }
        var empty = request("GET", exportPath.replace("offset=0", "offset=1"), reader, null, null);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).isEqualTo("invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n");
        for (String path : java.util.List.of(exportPath.replace("limit=20", "limit=101"),
                "/api/v1/admin/billing/invoices?limit=101", "/api/v1/admin/billing/subscriptions?limit=101")) {
            assertThat(request("GET", path, reader, null, null).statusCode()).as(path).isEqualTo(422);
        }
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from audit_logs entry", String.class)).isEqualTo(auditBefore);
        assertThat(jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from outbox_events entry", String.class)).isEqualTo(outboxBefore);
        assertThat(request("DELETE", "/api/v1/admin/billing/finance-access/" + reader.id(), bootstrap, null, null).statusCode()).isEqualTo(204);
        var revoked = request("GET", exportPath, reader, null, null);
        assertThat(revoked.statusCode()).isEqualTo(403);
        assertThat(revoked.body()).doesNotContain(invoiceId.toString(), privateName, privateNote);
        assertThat(financialSnapshot()).isEqualTo(before);
    }

    @Test void financeRolesSeparatePricingReaderOperatorAndApproverAndRevocationAffectsExistingJwt() throws Exception {
        Actor bootstrap = administrator(), reader = administrator(), operator = administrator(), approver = administrator();
        Actor owner = actor();
        UUID merchant = merchant(owner);
        settle(purchase(owner, merchant));
        enableFixtureFinanceRoles(bootstrap);
        assignFinanceRole(bootstrap, reader, "READER");
        assignFinanceRole(bootstrap, operator, "OPERATOR");
        assignFinanceRole(bootstrap, approver, "APPROVER");
        String grantPath = "/api/v1/admin/billing/merchants/" + merchant + "/grants";
        Map<String, String> before = financialSnapshot();
        assertThat(request("POST", "/api/v1/admin/billing/finance-access/" + bootstrap.id(), bootstrap,
            json.createObjectNode().put("role", "APPROVER"), null).statusCode()).isEqualTo(422);
        assertThat(request("POST", "/api/v1/admin/billing/finance-access/" + owner.id(), bootstrap,
            json.createObjectNode().put("role", "APPROVER"), null).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from billing_finance_permissions where user_id=?", Long.class, owner.id())).isZero();
        assertThat(request("GET", "/api/v1/admin/billing/invoices", bootstrap, null, null).statusCode()).isEqualTo(403);
        for (Actor denied : java.util.List.of(bootstrap, reader, operator)) {
            assertThat(request("POST", grantPath, denied, grantRequest(), null).statusCode()).isEqualTo(403);
        }
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(request("GET", "/api/v1/admin/billing/invoices?merchantId=" + merchant, reader, null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", grantPath, reader, null, null).statusCode()).isEqualTo(200);
        String planCode = createPricePlan(reader);
        assertThat(jdbc.queryForObject("select count(*) from billing_plans where code=?", Long.class, planCode)).isEqualTo(1L);
        var granted = request("POST", grantPath, approver, grantRequest(), null);
        assertThat(granted.statusCode()).as(granted.body()).isEqualTo(200);
        UUID grant = UUID.fromString(json.readTree(granted.body()).path("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_ADMIN_GRANT_CREATED'", Long.class, grant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='BILLING_ADMIN_GRANT_CREATED'", Long.class, grant)).isEqualTo(1L);
        Map<String, String> beforeFailure = financialSnapshot();
        long outboxBeforeFailure = jdbc.queryForObject("select count(*) from outbox_events where event_type='BILLING_ADMIN_GRANT_CREATED'", Long.class);
        jdbc.execute("""
            create function fixture_reject_grant_audit() returns trigger language plpgsql as $$
            begin if new.action='BILLING_ADMIN_GRANT_CREATED' then raise exception 'Fixture audit failure'; end if; return new; end; $$;
            create trigger fixture_reject_grant_audit before insert on audit_logs for each row execute function fixture_reject_grant_audit();
            """);
        try {
            assertThat(request("POST", grantPath, approver, grantRequest(), null).statusCode()).isEqualTo(500);
            assertThat(financialSnapshot()).isEqualTo(beforeFailure);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='BILLING_ADMIN_GRANT_CREATED'", Long.class)).isEqualTo(outboxBeforeFailure);
        } finally { jdbc.execute("drop trigger fixture_reject_grant_audit on audit_logs; drop function fixture_reject_grant_audit()"); }
        assertThat(request("DELETE", "/api/v1/admin/billing/finance-access/" + approver.id(), bootstrap, null, null).statusCode()).isEqualTo(204);
        assertThat(request("POST", grantPath, approver, grantRequest(), null).statusCode()).isEqualTo(403);
        assertThat(financialSnapshot()).isEqualTo(beforeFailure);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_FINANCE_ROLE_REVOKED'", Long.class, approver.id())).isEqualTo(1L);
    }

    private ObjectNode financeResolution(String status, String reference) {
        ObjectNode request = json.createObjectNode().put("status", status).put("note", "Disposable local finance workflow fixture");
        if (reference != null) request.put("externalReference", reference);
        return request;
    }

    @Test void refundCaseRequiresIndependentReviewApprovalAndPreservesUnrelatedGrantsAndInvoiceHistory() throws Exception {
        Actor bootstrap = administrator(), requester = administrator(), reviewer = administrator(), approver = administrator(), reader = administrator();
        UUID merchant = merchant(requester);
        JsonNode payment = purchase(requester, merchant);
        settle(payment);
        enableFixtureBilling(requester);
        enableFixtureFinanceRoles(bootstrap);
        assignFinanceRole(bootstrap, requester, "APPROVER");
        assignFinanceRole(bootstrap, reviewer, "OPERATOR");
        assignFinanceRole(bootstrap, approver, "APPROVER");
        assignFinanceRole(bootstrap, reader, "READER");
        UUID invoice = UUID.fromString(payment.path("invoiceId").asText());
        JsonNode originalInvoice = json.readTree(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice));
        var otherGrant = request("POST", "/api/v1/admin/billing/merchants/" + merchant + "/grants", approver, grantRequest(), null);
        assertThat(otherGrant.statusCode()).isEqualTo(200);
        UUID otherGrantId = UUID.fromString(json.readTree(otherGrant.body()).path("id").asText());
        String otherBefore = jdbc.queryForObject("select to_jsonb(g)::text from billing_entitlement_grants g where id=?", String.class, otherGrantId);
        String casePath = base(merchant) + "/invoices/" + invoice + "/finance-cases";
        ObjectNode claim = json.createObjectNode().put("kind", "REFUND").put("reason", "Disposable fixture refund request, no bank transaction");
        var created = request("POST", casePath, requester, claim, null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        UUID caseId = UUID.fromString(json.readTree(created.body()).path("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_FINANCE_CASE_REQUESTED'", Long.class, caseId)).isEqualTo(1L);
        var repeated = request("POST", casePath, requester, claim, null);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(repeated.body()).path("id").asText()).isEqualTo(caseId.toString());
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='BILLING_FINANCE_CASE_REQUESTED'", Long.class, caseId)).isEqualTo(1L);
        String resolve = "/api/v1/admin/billing/finance-cases/" + caseId + "/resolve";
        Map<String, String> beforeReview = financialSnapshot();
        assertThat(request("POST", resolve, reader, financeResolution("REVIEWING", null), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", resolve, reader, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", resolve, requester, financeResolution("REVIEWING", null), null).statusCode()).isEqualTo(409);
        assertThat(request("POST", resolve, approver, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null).statusCode()).isEqualTo(409);
        assertThat(financialSnapshot()).isEqualTo(beforeReview);
        assertThat(request("POST", resolve, reviewer, financeResolution("REVIEWING", null), null).statusCode()).isEqualTo(200);
        assertThat(request("POST", resolve, reviewer, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null).statusCode()).isEqualTo(403);
        assignFinanceRole(bootstrap, reviewer, "APPROVER");
        Map<String, String> reviewed = financialSnapshot();
        for (Actor selfApprover : java.util.List.of(requester, reviewer)) {
            assertThat(request("POST", resolve, selfApprover, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null).statusCode()).isEqualTo(409);
        }
        assertThat(financialSnapshot()).isEqualTo(reviewed);
        var confirmed = request("POST", resolve, approver, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null);
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(json.readTree(confirmed.body()).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select reviewed_by_user_id from billing_finance_cases where id=?", UUID.class, caseId)).isEqualTo(reviewer.id());
        assertThat(jdbc.queryForObject("select resolved_by_user_id from billing_finance_cases where id=?", UUID.class, caseId)).isEqualTo(approver.id());
        ObjectNode refundedInvoice = (ObjectNode) json.readTree(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice));
        assertThat(refundedInvoice.path("status").asText()).isEqualTo("REFUNDED");
        ObjectNode immutableOriginal = ((ObjectNode) originalInvoice).deepCopy();
        immutableOriginal.remove(java.util.List.of("status", "version"));
        refundedInvoice.remove(java.util.List.of("status", "version"));
        assertThat(refundedInvoice).isEqualTo(immutableOriginal);
        assertThat(jdbc.queryForObject("select status from billing_payment_attempts where invoice_id=?", String.class, invoice)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_type='PAID_INVOICE' and source_id=? and revoked_at is not null", Long.class, invoice)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select to_jsonb(g)::text from billing_entitlement_grants g where id=?", String.class, otherGrantId)).isEqualTo(otherBefore);
        assertThat(json.readTree(request("GET", base(merchant) + "/summary", requester, null, null).body()).path("canSell").asBoolean()).isTrue();
        Map<String, String> after = financialSnapshot();
        assertThat(request("POST", resolve, approver, financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null).statusCode()).isEqualTo(409);
        assertThat(financialSnapshot()).isEqualTo(after);
        for (String action : new String[]{"REQUESTED", "REVIEWING", "CONFIRMED"}) {
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action=?", Long.class, caseId,
                "BILLING_FINANCE_CASE_" + action)).isEqualTo(1L);
        }
        assertThat(provider.creations).hasValue(1);
        UUID disputedMerchant = merchant(requester);
        JsonNode disputedPayment = purchase(requester, disputedMerchant);
        settle(disputedPayment);
        UUID disputedInvoice = UUID.fromString(disputedPayment.path("invoiceId").asText());
        var disputed = request("POST", base(disputedMerchant) + "/invoices/" + disputedInvoice + "/finance-cases", requester,
            json.createObjectNode().put("kind", "DISPUTE").put("reason", "Disposable chargeback investigation fixture"), null);
        assertThat(disputed.statusCode()).isEqualTo(200);
        UUID disputeCase = UUID.fromString(json.readTree(disputed.body()).path("id").asText());
        String disputePath = "/api/v1/admin/billing/finance-cases/" + disputeCase + "/resolve";
        assertThat(request("POST", disputePath, reviewer, financeResolution("REVIEWING", null), null).statusCode()).isEqualTo(200);
        Map<String, String> disputeFinances = new LinkedHashMap<>(financialSnapshot());
        disputeFinances.remove("billing_finance_cases");
        var forbiddenRefund = request("POST", disputePath, approver,
            financeResolution("CONFIRMED", "FIXTURE-NO-BANK-TRANSFER"), null);
        assertThat(forbiddenRefund.statusCode()).isEqualTo(409);
        assertThat(forbiddenRefund.body()).contains("Disputes require investigation");
        assertThat(request("POST", disputePath, reviewer, financeResolution("REJECTED", null), null).statusCode()).isEqualTo(200);
        Map<String, String> afterDispute = new LinkedHashMap<>(financialSnapshot());
        afterDispute.remove("billing_finance_cases");
        assertThat(afterDispute).isEqualTo(disputeFinances);
        assertThat(jdbc.queryForObject("select status from billing_invoices where id=?", String.class, disputedInvoice)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_FINANCE_CASE_REJECTED'", Long.class, disputeCase)).isEqualTo(1L);
        assertThat(provider.creations).hasValue(2);
    }

    @Test void lifecycleExpiryIsAuditedOnceWithoutRewritingInvoiceOrGrantHistory() throws Exception {
        Actor owner = actor();
        UUID merchant = merchant(owner);
        settle(purchase(owner, merchant));
        Instant end = Instant.now().minus(Duration.ofDays(4)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), "ACTIVE");
        Map<String, String> before = new LinkedHashMap<>(financialSnapshot());
        before.remove("merchant_subscriptions");
        lifecycle.refreshStatuses();
        assertThat(jdbc.queryForObject("select status from merchant_subscriptions where merchant_id=?", String.class, merchant)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_SUBSCRIPTION_EXPIRED'", Long.class, merchant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select request_id from audit_logs where entity_id=? and action='BILLING_SUBSCRIPTION_EXPIRED'", String.class, merchant)).startsWith("billing-lifecycle-");
        assertThat(jdbc.queryForObject("select actor_type from audit_logs where entity_id=? and action='BILLING_SUBSCRIPTION_EXPIRED'", String.class, merchant)).isEqualTo("SYSTEM");
        assertThat(jdbc.queryForObject("select after_snapshot->>'source' from audit_logs where entity_id=? and action='BILLING_SUBSCRIPTION_EXPIRED'", String.class, merchant)).isEqualTo("BILLING_LIFECYCLE_SCHEDULER");
        String expired = jdbc.queryForObject("select to_jsonb(s)::text from merchant_subscriptions s where merchant_id=?", String.class, merchant);
        lifecycle.refreshStatuses();
        assertThat(jdbc.queryForObject("select to_jsonb(s)::text from merchant_subscriptions s where merchant_id=?", String.class, merchant)).isEqualTo(expired);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_SUBSCRIPTION_EXPIRED'", Long.class, merchant)).isEqualTo(1L);
        Map<String, String> after = new LinkedHashMap<>(financialSnapshot());
        after.remove("merchant_subscriptions");
        assertThat(after).isEqualTo(before);
        assertThat(provider.creations).hasValue(1);
    }

    private void enableFixtureBilling(Actor owner) {
        jdbc.update("""
            insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at)
            values (?,'billing.enforcement_enabled','BOOLEAN','true'::jsonb,'GLOBAL',?,now(),now())
            """, UUID.randomUUID(), owner.id());
    }

    private UUID trackedOffer(UUID merchant) {
        UUID location = UUID.randomUUID(), category = UUID.randomUUID(), item = UUID.randomUUID();
        UUID variant = UUID.randomUUID(), offer = UUID.randomUUID();
        jdbc.update("""
            insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,
                location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,
                currency_code,created_at,updated_at,version)
            values (?,?,'Boundary location','Fixture address',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),
                'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,0,'VND',now(),now(),0)
            """, location, merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Boundary category',?,'PRODUCT','ACTIVE')", category, "boundary-" + category);
        jdbc.update("""
            insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,
                listing_status,moderation_status,created_at,updated_at,version)
            values (?,?,'PRODUCT','STANDARD',?,'Boundary item',?,'ACTIVE','APPROVED',now(),now(),0)
            """, item, merchant, category, "boundary-" + item);
        jdbc.update("insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) values (?,?,'BOUNDARY','Default',true,'ACTIVE',0,now(),now(),0)", variant, item);
        jdbc.update("""
            insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,
                inventory_mode,preparation_time_minutes,created_at,updated_at,version)
            values (?,?,?,10000,'VND',true,'TRACKED',0,now(),now(),0)
            """, offer, location, variant);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,5,0,now(),0)", offer);
        return offer;
    }

    private ObjectNode buyerRequest(UUID offer) {
        ObjectNode body = json.createObjectNode().put("fulfillmentMethod", "PICKUP").put("paymentMethod", "COD");
        body.putObject("source").put("type", "DIRECT");
        body.putArray("items").addObject().put("locationVariantId", offer.toString()).put("quantity", 1);
        return body;
    }

    private ObjectNode buyerPreview(Actor buyer, UUID offer) throws Exception {
        var response = request("POST", "/api/v1/checkouts/preview", buyer, buyerRequest(offer), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.createObjectNode().put("checkoutToken", json.readTree(response.body()).path("checkoutToken").asText());
    }

    private UUID buyerOrder(Actor buyer, UUID offer) throws Exception {
        var response = request("POST", "/api/v1/checkouts/commit", buyer, buyerPreview(buyer, offer), "boundary-order-" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private void fixtureTerm(UUID merchant, Instant end, Instant graceUntil, String status) {
        Timestamp start = Timestamp.from(end.minusSeconds(3600)), finish = Timestamp.from(end);
        assertThat(jdbc.update("update billing_entitlement_grants set valid_from=?,valid_until=? where merchant_id=? and source_type='PAID_INVOICE'",
            start, finish, merchant)).isEqualTo(1);
        assertThat(jdbc.update("update merchant_subscriptions set period_start=?,period_end=?,grace_until=?,status=? where merchant_id=?",
            start, finish, Timestamp.from(graceUntil), status, merchant)).isEqualTo(1);
    }

    private JsonNode fixtureAd(Actor actor, UUID merchant, String target) throws Exception {
        Instant now = Instant.now();
        ObjectNode body = json.createObjectNode().put("title", "Billing expiry fixture")
            .put("body", "Disposable advertisement").put("ctaLabel", "View")
            .put("targetUrl", target).put("creativeType", "CSS").put("template", "NEARBY")
            .put("placement", "IN_FEED").put("device", "ALL").put("startsAt", now.minusSeconds(10).toString())
            .put("endsAt", now.plusSeconds(3600).toString()).put("enabled", true).put("priority", 1000);
        var response = request("POST", merchant == null ? "/api/v1/admin/ads" : "/api/v1/merchants/" + merchant + "/ads",
            actor, body, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body());
    }

    private JsonNode fixtureApprovedAd(Actor owner, Actor admin, UUID merchant, String target) throws Exception {
        JsonNode campaign = fixtureAd(merchant == null ? admin : owner, merchant, target);
        if (merchant == null) return campaign;
        String path = "/api/v1/merchants/" + merchant + "/ads/" + campaign.path("id").asText() + "/submit";
        var submitted = requestWithHeaders("POST", path, owner, null, null, Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(0)));
        assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(200);
        var approved = requestWithHeaders("POST", "/api/v1/admin/ads/" + campaign.path("id").asText() + "/review",
            admin, json.createObjectNode().put("approved", true), null, Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(1)));
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        return json.readTree(approved.body());
    }

    private JsonNode adDelivery(UUID campaign) throws Exception {
        var response = request("GET", "/api/v1/ads/active?placement=IN_FEED&device=MOBILE", null, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return java.util.stream.StreamSupport.stream(json.readTree(response.body()).spliterator(), false)
            .filter(delivery -> delivery.path("id").asText().equals(campaign.toString())).findFirst().orElse(null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"SELLER,PAST_DUE", "ADMIN_MERCHANT,PAST_DUE", "ADMIN_ITEM,PAST_DUE",
        "SELLER,EXPIRED", "ADMIN_MERCHANT,EXPIRED", "ADMIN_ITEM,EXPIRED",
        "ADMIN_ABSOLUTE_MERCHANT,PAST_DUE", "ADMIN_ABSOLUTE_ITEM,PAST_DUE",
        "ADMIN_ABSOLUTE_MERCHANT,EXPIRED", "ADMIN_ABSOLUTE_ITEM,EXPIRED",
        "ADMIN_DOT_MERCHANT,PAST_DUE", "ADMIN_DOT_MERCHANT,EXPIRED",
        "ADMIN_ENCODED_ITEM,PAST_DUE", "ADMIN_ENCODED_ITEM,EXPIRED"})
    void merchantLinkedAdsPauseAtBillingExpiryWithoutChangingCampaignOrFinance(String source, String status) throws Exception {
        Actor owner = actor(), admin = administrator();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        UUID item = jdbc.queryForObject("select v.catalog_item_id from location_variants lv join variants v on v.id=lv.variant_id where lv.id=?",
            UUID.class, offer);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        String destination = source.endsWith("ITEM") ? "/items/" + item : "/merchants/" + merchant;
        if (source.startsWith("ADMIN_ABSOLUTE")) destination = (source.endsWith("ITEM")
            ? "https://BILLING-fixture.invalid:443" : "https://billing-fixture.invalid") + destination;
        if (source.startsWith("ADMIN_DOT")) destination = "/search/.." + destination;
        if (source.startsWith("ADMIN_ENCODED")) destination = "https://billing-fixture.invalid/search/%2e%2e" + destination;
        JsonNode campaign = fixtureApprovedAd(owner, admin, source.equals("SELLER") ? merchant : null, destination);
        UUID campaignId = UUID.fromString(campaign.path("id").asText());
        JsonNode delivery = adDelivery(campaignId);
        assertThat(delivery).isNotNull();
        JsonNode draft = source.equals("SELLER") ? fixtureAd(owner, merchant, "/merchants/" + merchant) : null;
        Instant end = Instant.now().minus(status.equals("PAST_DUE") ? Duration.ofSeconds(1) : Duration.ofDays(4));
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), status);
        var finances = financialSnapshot();
        String storedCampaign = jdbc.queryForObject("select to_jsonb(c)::text from ad_campaigns c where id=?", String.class, campaignId);
        long events = jdbc.queryForObject("select count(*) from ad_events where campaign_id=?", Long.class, campaignId);
        var assertions = new org.assertj.core.api.SoftAssertions();
        assertions.assertThat(adDelivery(campaignId)).as("unavailable merchant must not receive new delivery").isNull();
        var event = request("POST", "/api/v1/ads/" + campaignId + "/events", null,
            json.createObjectNode().put("deliveryToken", delivery.path("deliveryToken").asText())
                .put("eventId", UUID.randomUUID().toString()).put("type", "CLICK").put("visibleMillis", 0), null);
        assertions.assertThat(event.statusCode()).as(event.body()).isEqualTo(422);
        assertions.assertThat(jdbc.queryForObject("select count(*) from ad_events where campaign_id=?", Long.class, campaignId)).isEqualTo(events);
        assertions.assertThat(financialSnapshot()).isEqualTo(finances);
        assertions.assertThat(jdbc.queryForObject("select to_jsonb(c)::text from ad_campaigns c where id=?", String.class, campaignId)).isEqualTo(storedCampaign);
        assertions.assertAll();
        assertThat(request("GET", base(merchant) + "/invoices", owner, null, null).statusCode()).isEqualTo(200);
        if (draft != null) {
            assertBillingDenied(requestWithHeaders("POST", "/api/v1/merchants/" + merchant + "/ads/"
                + draft.path("id").asText() + "/submit", owner, null, null, Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(0))));
            assertThat(request("GET", "/api/v1/merchants/" + merchant + "/ads", owner, null, null).statusCode()).isEqualTo(200);
        }
        UUID unrelated = UUID.fromString(fixtureAd(admin, null, "/search").path("id").asText());
        assertThat(adDelivery(unrelated)).isNotNull();
        assertThat(financialSnapshot()).isEqualTo(finances);
        fixtureTerm(merchant, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), "ACTIVE");
        assertThat(adDelivery(campaignId)).isNotNull();
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), status);
        clearFixtureEnforcement();
        assertThat(adDelivery(campaignId)).isNotNull();
        assertThat(provider.creations).hasValue(1);
    }

    @Test void merchantLinkedAdDeliveryCannotOutliveActiveEntitlementOrCampaign() throws Exception {
        Actor owner = actor(), admin = administrator();
        UUID merchant = merchant(owner);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        Instant end = Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, end, end.plus(Duration.ofDays(3)), "ACTIVE");
        JsonNode campaign = fixtureApprovedAd(owner, admin, merchant, "/merchants/" + merchant);
        UUID campaignId = UUID.fromString(campaign.path("id").asText());
        JsonNode delivery = adDelivery(campaignId);
        assertThat(delivery).isNotNull();
        assertThat(Instant.parse(delivery.path("deliveryExpiresAt").asText())).isBeforeOrEqualTo(end);
        assertThat(Instant.parse(delivery.path("deliveryExpiresAt").asText()))
            .isBeforeOrEqualTo(Instant.parse(campaign.path("endsAt").asText()));
        ObjectNode payload = (ObjectNode) json.readTree(jdbc.queryForObject("select payload::text from ad_campaigns where id=?", String.class, campaignId));
        Instant campaignEnd = Instant.now().plusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var shortened = requestWithHeaders("PATCH", "/api/v1/admin/ads/" + campaignId, admin,
            payload.put("endsAt", campaignEnd.toString()), null, Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(2)));
        assertThat(shortened.statusCode()).as(shortened.body()).isEqualTo(200);
        assertThat(Instant.parse(adDelivery(campaignId).path("deliveryExpiresAt").asText())).isEqualTo(campaignEnd);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void billingOffDoesNotResolveAdsBeyondTheExistingTenDeliveryLimit(boolean enforced) throws Exception {
        Actor owner = actor(), admin = administrator();
        UUID merchant = merchant(owner);
        if (enforced) {
            settle(purchase(owner, merchant));
            enableFixtureBilling(owner);
        }
        UUID campaign = UUID.fromString(fixtureAd(admin, null, "/merchants/" + merchant).path("id").asText());
        jdbc.update("update ad_campaigns set placement='ITEM_DETAIL',payload=jsonb_set(payload,'{placement}','\"ITEM_DETAIL\"'::jsonb) where id=?", campaign);
        cloneFixtureAds(campaign, 9);
        String path = "/api/v1/ads/active?placement=ITEM_DETAIL&device=MOBILE";
        int ten = measuredReadStatements(path, null, "", 10);
        cloneFixtureAds(campaign, 10);
        int twenty = measuredReadStatements(path, null, "", 10);
        assertThat(twenty).as("destination reads must stop after ten deliverable ads, enforcement=" + enforced).isEqualTo(ten);
    }

    private void cloneFixtureAds(UUID campaign, int count) {
        jdbc.update("""
            insert into ad_campaigns(id,merchant_id,owner_user_id,payload,placement,device,status,enabled,priority,starts_at,ends_at,created_at,updated_at)
            select gen_random_uuid(),merchant_id,owner_user_id,payload,placement,device,status,enabled,priority,starts_at,ends_at,created_at,updated_at
            from ad_campaigns cross join generate_series(1,?) where id=?
            """, count, campaign);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"https://external.invalid", "https://billing-fixture.invalid:444"})
    void genuinelyExternalAdOriginsKeepTheirExistingBehavior(String origin) throws Exception {
        Actor owner = actor(), admin = administrator();
        UUID merchant = merchant(owner);
        enableFixtureBilling(owner);
        UUID campaign = UUID.fromString(fixtureAd(admin, null, origin + "/merchants/" + merchant).path("id").asText());
        var before = financialSnapshot();
        JsonNode delivery = adDelivery(campaign);
        assertThat(delivery).isNotNull();
        assertThat(delivery.path("targetUrl").asText()).isEqualTo(origin + "/merchants/" + merchant);
        assertThat(request("POST", "/api/v1/ads/" + campaign + "/events", null,
            json.createObjectNode().put("deliveryToken", delivery.path("deliveryToken").asText())
                .put("eventId", UUID.randomUUID().toString()).put("type", "CLICK").put("visibleMillis", 0), null).statusCode()).isEqualTo(204);
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    private Map<String, String> commerceSnapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : new String[]{"orders", "order_items", "product_fulfillments", "order_events",
                "inventory_balances", "inventory_reservations", "inventory_movements", "checkout_sessions", "outbox_events"}) {
            result.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(row_data order by row_data::text),'[]'::jsonb)::text from (select to_jsonb(existing) row_data from " + table + " existing) records", String.class));
        }
        return result;
    }

    private void assertBillingDenied(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json.readTree(response.body()).path("error").path("code").asText()).isEqualTo("BILLING_SUBSCRIPTION_REQUIRED");
    }

    @Test void graceReadOnlyDeniesNewPreviewAndCommitButExistingOrderCanComplete() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        JsonNode invoice = purchase(owner, merchant);
        settle(invoice);
        enableFixtureBilling(owner);
        UUID oldOrder = buyerOrder(buyer, offer);
        ObjectNode pending = buyerPreview(buyer, offer);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        String invoiceBefore = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoiceId);
        Instant ended = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, ended, ended.plus(Duration.ofDays(3)), "PAST_DUE");
        JsonNode summary = json.readTree(request("GET", base(merchant) + "/summary", owner, null, null).body());
        assertThat(summary.path("canSell").asBoolean()).isFalse();
        assertThat(summary.path("reasonCode").asText()).isEqualTo("GRACE_PERIOD_READ_ONLY");
        Map<String, String> before = commerceSnapshot();
        assertBillingDenied(request("POST", "/api/v1/checkouts/preview", buyer, buyerRequest(offer), null));
        assertBillingDenied(request("POST", "/api/v1/checkouts/commit", buyer, pending, "grace-denied-" + UUID.randomUUID()));
        assertThat(commerceSnapshot()).isEqualTo(before);
        String route = "/api/v1/orders/" + oldOrder;
        assertThat(request("GET", route, owner, null, null).statusCode()).isEqualTo(200);
        for (String operation : new String[]{"confirm", "mark-ready-for-pickup"}) {
            assertThat(request("POST", route + "/" + operation, owner, null, "grace-fulfill-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        }
        assertThat(request("POST", route + "/confirm-received", buyer, null, "grace-received-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        assertThat(json.readTree(request("GET", route, buyer, null, null).body()).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, oldOrder)).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Long.class, oldOrder)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoiceId)).isEqualTo(invoiceBefore);
    }

    @Test void entitlementExpiresDuringActualInventoryLockWaitAndCommitPreservesExistingOrder() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        UUID oldOrder = buyerOrder(buyer, offer);
        ObjectNode pending = buyerPreview(buyer, offer);
        Instant expires = Instant.now().plusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, expires, expires.plus(Duration.ofDays(3)), "ACTIVE");
        Map<String, String> before = commerceSnapshot();
        try (var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
             var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("select location_variant_id from inventory_balances where location_variant_id=? for update")) {
                    statement.setObject(1, offer); statement.executeQuery().close();
                }
                var commit = workers.submit(() -> request("POST", "/api/v1/checkouts/commit", buyer, pending, "expiry-denied-" + UUID.randomUUID()));
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%inventory_balances%'", Integer.class);
                    if (waiting == 0) Thread.sleep(20);
                } while (waiting == 0 && System.nanoTime() < deadline);
                assertThat(waiting).as("actual checkout passed its first entitlement check and waits on inventory").isEqualTo(1);
                assertThat(Instant.now()).as("entitlement still valid when checkout reaches the lock").isBefore(expires);
                Thread.sleep(Math.max(0, Duration.between(Instant.now(), expires.plusMillis(50)).toMillis()));
                blocker.commit();
                assertBillingDenied(commit.get(5, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
        assertThat(commerceSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from orders where id=?", String.class, oldOrder)).isEqualTo("PENDING_CONFIRMATION");
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isEqualTo(1L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SUSPENDED", "HIDDEN"})
    void settlementCannotOverwriteModerationOrEnableBuyerCheckout(String state) throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        JsonNode invoice = purchase(owner, merchant);
        if (state.equals("SUSPENDED")) jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?", merchant);
        else jdbc.update("update merchant_profiles set moderation_status='HIDDEN' where id=?", merchant);
        String before = jdbc.queryForObject("select to_jsonb(m)::text from merchant_profiles m where id=?", String.class, merchant);
        settle(invoice);
        enableFixtureBilling(owner);
        assertThat(jdbc.queryForObject("select to_jsonb(m)::text from merchant_profiles m where id=?", String.class, merchant)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from billing_invoices where id=?", String.class,
            UUID.fromString(invoice.path("invoiceId").asText()))).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=? and revoked_at is null and valid_until>now()", Long.class, merchant)).isEqualTo(1L);
        var summary = request("GET", base(merchant) + "/summary", owner, null, null);
        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(json.readTree(summary.body()).path("canSell").asBoolean()).isTrue();
        Map<String, String> commerceBefore = commerceSnapshot();
        var denied = request("POST", "/api/v1/checkouts/preview", buyer, buyerRequest(offer), null);
        assertThat(denied.statusCode()).isEqualTo(422);
        assertThat(json.readTree(denied.body()).path("error").path("code").asText()).isEqualTo("OFFER_NOT_AVAILABLE");
        assertThat(commerceSnapshot()).isEqualTo(commerceBefore);
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isZero();
        assertThat(jdbc.queryForObject("select to_jsonb(m)::text from merchant_profiles m where id=?", String.class, merchant)).isEqualTo(before);
    }

    @Test void buyerCodAndNegotiatedBankOrdersNeverBecomeSellerPayosPayments() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        JsonNode invoice = purchase(owner, merchant);
        settle(invoice);
        enableFixtureBilling(owner);
        Map<String, String> before = financialSnapshot();
        for (String method : new String[]{"COD", "BANK_TRANSFER_NEGOTIATED"}) {
            ObjectNode body = buyerRequest(offer).put("paymentMethod", method);
            var preview = request("POST", "/api/v1/checkouts/preview", buyer, body, null);
            assertThat(preview.statusCode()).as(method).isEqualTo(200);
            ObjectNode commit = json.createObjectNode().put("checkoutToken", json.readTree(preview.body()).path("checkoutToken").asText());
            var created = request("POST", "/api/v1/checkouts/commit", buyer, commit, "buyer-method-" + UUID.randomUUID());
            assertThat(created.statusCode()).as(method).isEqualTo(201);
            UUID order = UUID.fromString(json.readTree(created.body()).path("id").asText());
            assertThat(jdbc.queryForObject("select payment_method from orders where id=?", String.class, order)).isEqualTo(method);
            assertThat(jdbc.queryForObject("select merchant_profile_id from orders where id=?", UUID.class, order)).isEqualTo(merchant);
            assertThat(jdbc.queryForObject("select total_amount from orders where id=?", Long.class, order)).isEqualTo(10000L);
            assertThat(request("GET", "/api/v1/orders/" + order, buyer, null, null).statusCode()).isEqualTo(200);
            assertThat(financialSnapshot()).isEqualTo(before);
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select provider from billing_payment_attempts where invoice_id=?", String.class,
            UUID.fromString(invoice.path("invoiceId").asText()))).isEqualTo("PAYOS");
        assertThat(provider.creations).hasValue(1);
    }

    @Test void disablingEnforcementRollsBackOnlyTheGateWithoutChangingFinanceOrExistingOrders() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), offer = trackedOffer(merchant);
        settle(purchase(owner, merchant));
        enableFixtureBilling(owner);
        UUID oldOrder = buyerOrder(buyer, offer);
        Instant ended = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        fixtureTerm(merchant, ended, ended, "EXPIRED");
        assertBillingDenied(request("POST", "/api/v1/checkouts/preview", buyer, buyerRequest(offer), null));
        Map<String, String> finances = financialSnapshot(), commerce = commerceSnapshot();
        String historicOrder = jdbc.queryForObject("select to_jsonb(o)::text from orders o where id=?", String.class, oldOrder);
        assertThat(jdbc.update("delete from system_settings where setting_key='billing.enforcement_enabled' and scope_type='GLOBAL'")).isEqualTo(1);
        var summary = request("GET", base(merchant) + "/summary", owner, null, null);
        assertThat(summary.statusCode()).isEqualTo(200);
        JsonNode eligibility = json.readTree(summary.body());
        assertThat(eligibility.path("enforced").asBoolean()).isFalse();
        assertThat(eligibility.path("canSell").asBoolean()).isTrue();
        assertThat(eligibility.path("reasonCode").asText()).isEqualTo("ENFORCEMENT_DISABLED");
        assertThat(financialSnapshot()).isEqualTo(finances);
        assertThat(commerceSnapshot()).isEqualTo(commerce);
        UUID newOrder = buyerOrder(buyer, offer);
        assertThat(newOrder).isNotEqualTo(oldOrder);
        assertThat(jdbc.queryForObject("select to_jsonb(o)::text from orders o where id=?", String.class, oldOrder)).isEqualTo(historicOrder);
        String route = "/api/v1/orders/" + oldOrder;
        for (String operation : new String[]{"confirm", "mark-ready-for-pickup"}) {
            assertThat(request("POST", route + "/" + operation, owner, null, "rollback-fulfill-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        }
        assertThat(request("POST", route + "/confirm-received", buyer, null, "rollback-received-" + UUID.randomUUID()).statusCode()).isEqualTo(200);
        assertThat(json.readTree(request("GET", route, buyer, null, null).body()).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isEqualTo(2L);
        assertThat(financialSnapshot()).isEqualTo(finances);
        assertThat(provider.creations).hasValue(1);
    }

    @Test void unpaidOwnerCreatesDraftButCannotPublishActivateOrSellThroughDirectHttp() throws Exception {
        Actor owner = actor(), buyer = actor();
        UUID merchant = merchant(owner), location = UUID.randomUUID(), category = UUID.randomUUID();
        jdbc.update("""
            insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at)
            values (?,'billing.enforcement_enabled','BOOLEAN','true'::jsonb,'GLOBAL',?,now(),now())
            """, UUID.randomUUID(), owner.id());
        jdbc.update("""
            insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,
                location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,
                currency_code,created_at,updated_at,version)
            values (?,?,'Unpaid draft location','Fixture address',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),
                'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,0,'VND',now(),now(),0)
            """, location, merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Unpaid draft category',?,'PRODUCT','ACTIVE')",
            category, "billing-draft-" + category);
        ObjectNode body = json.createObjectNode().put("type", "PRODUCT").put("listingKind", "STANDARD")
            .put("categoryId", category.toString()).put("name", "Unpaid draft item");
        body.putArray("locationOffers").addObject().put("merchantLocationId", location.toString())
            .put("variantClientKey", "__default__").put("price", 25000).put("currency", "VND")
            .put("available", true).put("inventoryMode", "UNTRACKED");
        String route = "/api/v1/merchants/" + merchant + "/catalog-items";
        var created = request("POST", route, owner, body, "unpaid-draft-" + UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode draft = json.readTree(created.body());
        assertThat(draft.path("listingStatus").asText()).isEqualTo("DRAFT");
        UUID item = UUID.fromString(draft.path("id").asText());
        String merchantRoute = "/api/v1/merchants/" + merchant;
        var profile = request("GET", merchantRoute, owner, null, null);
        assertThat(profile.statusCode()).isEqualTo(200);
        long profileVersion = json.readTree(profile.body()).path("version").asLong();
        var editedProfile = requestWithHeaders("PATCH", merchantRoute, owner,
            json.createObjectNode().put("name", "Unpaid owner profile edit"), null,
            Map.of("If-Match", "\"v" + profileVersion + "\""));
        assertThat(editedProfile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(editedProfile.body()).path("name").asText()).isEqualTo("Unpaid owner profile edit");
        assertThat(request("GET", route + "/" + item, owner, null, null).statusCode()).isEqualTo(200);
        var editedDraft = requestWithHeaders("PATCH", route + "/" + item, owner,
            json.createObjectNode().put("name", "Unpaid editable draft"), null,
            Map.of("If-Match", "\"v" + draft.path("version").asLong() + "\""));
        assertThat(editedDraft.statusCode()).isEqualTo(204);
        var rereadDraft = request("GET", route + "/" + item, owner, null, null);
        assertThat(rereadDraft.statusCode()).isEqualTo(200);
        draft = json.readTree(rereadDraft.body());
        assertThat(draft.path("name").asText()).isEqualTo("Unpaid editable draft");
        assertThat(draft.path("listingStatus").asText()).isEqualTo("DRAFT");
        var billing = request("GET", base(merchant) + "/summary", owner, null, null);
        assertThat(billing.statusCode()).isEqualTo(200);
        assertThat(json.readTree(billing.body()).path("enforced").asBoolean()).isTrue();
        assertThat(json.readTree(billing.body()).path("canSell").asBoolean()).isFalse();
        var invoiceHistory = request("GET", base(merchant) + "/invoices", owner, null, null);
        assertThat(invoiceHistory.statusCode()).isEqualTo(200);
        assertThat(json.readTree(invoiceHistory.body()).size()).isZero();
        assertThat(request("GET", "/api/v1/billing/plans", owner, null, null).statusCode()).isEqualTo(200);
        assertThat(quote(owner, merchant).path("expectedAmountVnd").asLong()).isPositive();
        String before = jdbc.queryForObject("select to_jsonb(i)::text from catalog_items i where id=?", String.class, item);
        long events = jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, item);
        for (String operation : new String[]{"publish", "activate"}) {
            var denied = requestWithHeaders("POST", route + "/" + item + "/" + operation, owner, null, null,
                Map.of("If-Match", "\"v" + draft.path("version").asLong() + "\""));
            assertThat(denied.statusCode()).as(operation).isEqualTo(409);
            assertThat(json.readTree(denied.body()).path("error").path("code").asText()).isEqualTo("BILLING_SUBSCRIPTION_REQUIRED");
        }
        ObjectNode preview = json.createObjectNode().put("fulfillmentMethod", "PICKUP").put("paymentMethod", "COD");
        preview.putObject("source").put("type", "DIRECT");
        preview.putArray("items").addObject().put("locationVariantId", draft.path("locationOffers").get(0).path("id").asText()).put("quantity", 1);
        var unavailable = request("POST", "/api/v1/checkouts/preview", buyer, preview, null);
        assertThat(unavailable.statusCode()).isEqualTo(409);
        assertThat(json.readTree(unavailable.body()).path("error").path("code").asText()).isEqualTo("BILLING_SUBSCRIPTION_REQUIRED");
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from catalog_items i where id=?", String.class, item)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?", Long.class, item)).isEqualTo(events);
        assertThat(jdbc.queryForObject("select count(*) from orders where merchant_profile_id=?", Long.class, merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, merchant)).isZero();
        assertThat(provider.creations).hasValue(0);
    }

    @Test void publishedPriceNotifiesEverySubscribedOwnerOnceWithoutChangingFinances() throws Exception {
        Actor admin = administrator(), owner = actor(), staff = actor(), revoked = actor(), outsider = actor();
        String code = createPricePlan(admin);
        UUID firstShop = merchant(owner), secondShop = merchant(owner), revokedShop = merchant(revoked);
        member(firstShop, staff, "STAFF");
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=? and user_id=?",
            revokedShop, revoked.id());
        var subscribed = new java.util.ArrayList<UUID>(java.util.List.of(firstShop, secondShop, revokedShop));
        var expectedOwners = new java.util.HashSet<UUID>();
        expectedOwners.add(owner.id());
        for (int index = 0; index < 100; index++) {
            UUID user = UUID.randomUUID();
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Price notice fixture','ACTIVE',now(),now())", user);
            subscribed.add(merchant(new Actor(user, null)));
            expectedOwners.add(user);
        }
        merchant(outsider);
        for (UUID shop : subscribed) {
            jdbc.update("""
                insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,created_at,updated_at)
                values (?,?,'ACTIVE',now(),now()+interval '30 days',now(),now())
                """, shop, code);
        }
        Map<String, String> before = financialSnapshot();
        Instant effective = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var publication = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
            json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1).put("effectiveAt", effective.toString()),
            null, Map.of("If-Match", "\"1\""));
        assertThat(publication.statusCode()).isEqualTo(200);
        UUID version = UUID.fromString(json.readTree(publication.body()).path("id").asText());
        UUID event = jdbc.queryForObject("select id from outbox_events where event_type='BILLING_PRICE_PUBLISHED' and aggregate_id=?", UUID.class, version);
        for (int processed = 0; processed < 10000; processed++) {
            if ("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event))) break;
            assertThat(outboxProcessor.processOne()).isTrue();
        }
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForList("select user_id from notifications where source_outbox_event_id=?", UUID.class, event))
            .containsExactlyInAnyOrderElementsOf(expectedOwners);
        var response = request("GET", "/api/v1/me/notifications", owner, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode notice = json.readTree(response.body()).path("items").get(0);
        assertThat(notice.path("type").asText()).isEqualTo("BILLING_RENEWAL_REMINDER");
        JsonNode data = notice.path("data");
        assertThat(data.path("noticeKind").asText()).isEqualTo("PRICE_CHANGED");
        assertThat(data.path("planVersionId").asText()).isEqualTo(version.toString());
        assertThat(data.path("planCode").asText()).isEqualTo(code);
        assertThat(data.path("revision").asInt()).isEqualTo(2);
        assertThat(data.path("priceVnd").asLong()).isEqualTo(15000);
        assertThat(data.path("cycleMonths").asInt()).isEqualTo(1);
        assertThat(Instant.parse(data.path("effectiveAt").asText())).isEqualTo(effective);
        assertThat(data.path("deepLink").asText()).isEqualTo("/seller");
        assertThat(data.properties()).extracting(java.util.Map.Entry::getKey)
            .containsExactlyInAnyOrder("noticeKind", "planVersionId", "planCode", "revision", "priceVnd", "cycleMonths", "effectiveAt", "deepLink");
        for (Actor denied : java.util.List.of(staff, revoked, outsider)) {
            var inbox = request("GET", "/api/v1/me/notifications", denied, null, null);
            assertThat(inbox.statusCode()).isEqualTo(200);
            assertThat(json.readTree(inbox.body()).path("items")).isEmpty();
        }
        var context = new vn.covie.outbox.application.OutboxEventContext(event, "BILLING_PRICE_PUBLISHED",
            "BILLING_PLAN_VERSION", version, json.createObjectNode().put("planCode", "UNTRUSTED_PAYLOAD"), Instant.now());
        var replay = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
            .execute(status -> billingNotifications.handle(context));
        assertThat(replay).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=?", Long.class, event)).isEqualTo(101L);
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    @Test void adminPublicationPreviewsObservePopulationWithoutChangingFinancialState() throws Exception {
        Actor admin = administrator(), owner = actor();
        String code = createPricePlan(admin), emptyCode = createPricePlan(admin);
        for (String status : java.util.List.of("ACTIVE", "EXPIRED", "CANCELLED", "REVOKED")) {
            jdbc.update("""
                insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,created_at,updated_at)
                values (?,?,?,now(),now()+interval '30 days',now(),now())
                """, merchant(owner), code, status);
        }
        jdbc.update("""
            insert into merchant_subscriptions(merchant_id,plan_code,status,created_at,updated_at)
            values (?,?,'PENDING_PAYMENT',now(),now())
            """, merchant(owner), "SELLER_STANDARD");
        merchant(owner);
        var priceBody = json.createObjectNode().put("priceVnd", 0).put("cycleMonths", 2)
            .put("effectiveAt", Instant.now().plusSeconds(3600).toString());
        int policyRevision = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        Instant policyDate = jdbc.queryForObject("select max(effective_at) from billing_policy_versions", Timestamp.class)
            .toInstant().plusSeconds(3600);
        if (policyDate.isBefore(Instant.now())) policyDate = Instant.now().plusSeconds(3600);
        var policyBody = json.createObjectNode().put("onboardingMode", "TRIAL").put("trialDays", 14)
            .put("graceDays", 5).put("effectiveAt", policyDate.toString());
        long subscriptionCount = jdbc.queryForObject("select count(*) from merchant_subscriptions", Long.class);
        Map<String, String> before = publicationPreviewSnapshot();
        for (String plan : java.util.List.of(code, emptyCode)) {
            var response = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + plan + "/prices/preview",
                admin, priceBody, null, Map.of("If-Match", "\"1\""));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            JsonNode preview = json.readTree(response.body());
            assertThat(preview.path("previous").path("priceVnd").asLong()).isEqualTo(10000);
            assertThat(preview.path("previous").path("revision").asInt()).isEqualTo(1);
            assertThat(preview.path("proposed")).isEqualTo(priceBody);
            assertThat(preview.path("referencingMerchants").asLong()).isEqualTo(plan.equals(code) ? 4 : 0);
            assertThat(Instant.parse(preview.path("observedAt").asText())).isBeforeOrEqualTo(Instant.now());
        }
        var policyResponse = requestWithHeaders("POST", "/api/v1/admin/billing/policy/preview", admin,
            policyBody, null, Map.of("If-Match", "\"" + policyRevision + "\""));
        assertThat(policyResponse.statusCode()).as(policyResponse.body()).isEqualTo(200);
        assertThat(policyResponse.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode policyPreview = json.readTree(policyResponse.body());
        assertThat(policyPreview.path("previous").path("revision").asInt()).isEqualTo(policyRevision);
        assertThat(policyPreview.path("proposed")).isEqualTo(policyBody);
        assertThat(policyPreview.path("referencingMerchants").asLong()).isEqualTo(subscriptionCount);
        assertThat(publicationPreviewSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    @Test void publicationPreviewRejectsNonAdminStaleOrInvalidRequests() throws Exception {
        Actor admin = administrator(), nonAdmin = actor();
        String code = createPricePlan(admin);
        var priceBody = json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1)
            .put("effectiveAt", Instant.now().plusSeconds(3600).toString());
        var policyBody = json.createObjectNode().put("onboardingMode", "TRIAL").put("trialDays", 14)
            .put("graceDays", 3).put("effectiveAt", Instant.now().plusSeconds(86400).toString());
        Map<String, String> before = publicationPreviewSnapshot();
        for (String path : java.util.List.of("/api/v1/admin/billing/plans/" + code + "/prices/preview",
                "/api/v1/admin/billing/policy/preview")) {
            JsonNode body = path.contains("/prices/") ? priceBody : policyBody;
            assertThat(requestWithHeaders("POST", path, nonAdmin, body, null, Map.of("If-Match", "\"1\"")).statusCode()).isEqualTo(403);
            assertThat(requestWithHeaders("POST", path, null, body, null, Map.of("If-Match", "\"1\"")).statusCode()).isEqualTo(403);
            assertThat(requestWithHeaders("POST", path, admin, body, null, Map.of("If-Match", "\"999999\"")).statusCode()).isEqualTo(409);
            assertThat(requestWithHeaders("POST", path, admin, body, null, Map.of("If-Match", "1")).statusCode()).isEqualTo(422);
        }
        assertThat(requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices/preview", admin,
            priceBody.deepCopy().put("priceVnd", -1), null, Map.of("If-Match", "\"1\"")).statusCode()).isEqualTo(422);
        assertThat(requestWithHeaders("POST", "/api/v1/admin/billing/plans/UNKNOWN/prices/preview", admin,
            priceBody, null, Map.of("If-Match", "\"1\"")).statusCode()).isEqualTo(404);
        for (String path : java.util.List.of("/api/v1/admin/billing/plans", "/api/v1/admin/billing/policy/latest")) {
            assertThat(request("GET", path, nonAdmin, null, null).statusCode()).isEqualTo(403);
            assertThat(request("GET", path, null, null, null).statusCode()).isEqualTo(401);
        }
        assertThat(publicationPreviewSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    @Test void latestAdminPublicationsAllowReviewingTwoFutureRevisionsWithoutChangingEffectiveReads() throws Exception {
        Actor admin = administrator();
        String code = createPricePlan(admin);
        JsonNode effectivePlan = awaitPricePlan(code, 1);
        JsonNode effectivePolicy = json.readTree(request("GET", "/api/v1/admin/billing/policy", admin, null, null).body());
        int policyRevision = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        Instant policyStart = jdbc.queryForObject("select max(effective_at) from billing_policy_versions", Timestamp.class).toInstant();
        if (policyStart.isBefore(Instant.now())) policyStart = Instant.now();
        try {
            for (int step : java.util.List.of(1, 2)) {
                var plans = request("GET", "/api/v1/admin/billing/plans", admin, null, null);
                assertThat(plans.statusCode()).isEqualTo(200);
                assertThat(plans.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                JsonNode selected = null;
                for (JsonNode plan : json.readTree(plans.body())) if (plan.path("code").asText().equals(code)) selected = plan;
                assertThat(selected).isNotNull();
                assertThat(selected.path("revision").asInt()).isEqualTo(step);
                var priceBody = json.createObjectNode().put("priceVnd", 15000 + step).put("cycleMonths", 1)
                    .put("effectiveAt", Instant.now().plusSeconds(3600L * step).toString());
                String path = "/api/v1/admin/billing/plans/" + code + "/prices";
                Map<String, String> priceHeaders = Map.of("If-Match", "\"" + step + "\"");
                assertThat(requestWithHeaders("POST", path + "/preview", admin, priceBody, null, priceHeaders).statusCode()).isEqualTo(200);
                assertThat(requestWithHeaders("POST", path, admin, priceBody, null, priceHeaders).statusCode()).isEqualTo(200);
                assertThat(requestWithHeaders("POST", path + "/preview", admin, priceBody, null, priceHeaders).statusCode()).isEqualTo(409);
                var policy = request("GET", "/api/v1/admin/billing/policy/latest", admin, null, null);
                assertThat(policy.statusCode()).isEqualTo(200);
                assertThat(policy.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                assertThat(json.readTree(policy.body()).path("revision").asInt()).isEqualTo(policyRevision + step - 1);
                var policyBody = json.createObjectNode().put("onboardingMode", "TRIAL").put("trialDays", step)
                    .put("graceDays", 3).put("effectiveAt", policyStart.plusSeconds(3600L * step).toString());
                Map<String, String> policyHeaders = Map.of("If-Match", "\"" + (policyRevision + step - 1) + "\"");
                assertThat(requestWithHeaders("POST", "/api/v1/admin/billing/policy/preview", admin, policyBody, null, policyHeaders).statusCode()).isEqualTo(200);
                assertThat(requestWithHeaders("POST", "/api/v1/admin/billing/policy", admin, policyBody, null, policyHeaders).statusCode()).isEqualTo(200);
            }
            assertThat(awaitPricePlan(code, 1)).isEqualTo(effectivePlan);
            assertThat(json.readTree(request("GET", "/api/v1/admin/billing/policy", admin, null, null).body())).isEqualTo(effectivePolicy);
            assertThat(provider.creations).hasValue(0);
        } finally {
            jdbc.update("delete from outbox_events where aggregate_id in (select id from billing_policy_versions where revision>?)", policyRevision);
            jdbc.update("delete from audit_logs where entity_id in (select id from billing_policy_versions where revision>?)", policyRevision);
            jdbc.update("delete from billing_policy_versions where revision>?", policyRevision);
        }
    }

    @Test void adminPublicationHistoryReadsStoredTimesAndRetiredVersionsWithoutMutation() throws Exception {
        Actor admin = administrator(), nonAdmin = actor();
        String code = createPricePlan(admin);
        var price = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
            json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 2)
                .put("effectiveAt", Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()),
            null, Map.of("If-Match", "\"1\""));
        assertThat(price.statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/v1/admin/billing/plans/" + code + "/retire", admin, null, null).statusCode()).isEqualTo(204);
        Map<String, String> before = publicationPreviewSnapshot();
        String pricePath = "/api/v1/admin/billing/plans/" + code + "/prices/history";
        var response = request("GET", pricePath + "?limit=1&offset=0", admin, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode latest = json.readTree(response.body());
        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).path("version")).isEqualTo(json.readTree(price.body()));
        assertThat(latest.get(0).path("retired").asBoolean()).isTrue();
        Instant published = jdbc.queryForObject("select published_at from billing_plan_versions where plan_code=? and revision=2", Timestamp.class, code).toInstant();
        assertThat(Instant.parse(latest.get(0).path("publishedAt").asText())).isEqualTo(published);
        JsonNode previous = json.readTree(request("GET", pricePath + "?limit=1&offset=1", admin, null, null).body());
        assertThat(previous.get(0).path("version").path("revision").asInt()).isEqualTo(1);
        assertThat(json.readTree(request("GET", pricePath + "?limit=1&offset=2", admin, null, null).body())).isEmpty();
        String policyPath = "/api/v1/admin/billing/policy/history";
        var policies = request("GET", policyPath + "?limit=100", admin, null, null);
        assertThat(policies.statusCode()).as(policies.body()).isEqualTo(200);
        assertThat(policies.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        int lastRevision = Integer.MAX_VALUE;
        for (JsonNode entry : json.readTree(policies.body())) {
            int revision = entry.path("version").path("revision").asInt();
            assertThat(revision).isLessThan(lastRevision);
            lastRevision = revision;
            Instant storedCreated = jdbc.queryForObject("select created_at from billing_policy_versions where revision=?", Timestamp.class, revision).toInstant();
            assertThat(Instant.parse(entry.path("publishedAt").asText())).isEqualTo(storedCreated);
        }
        assertThat(lastRevision).isLessThan(Integer.MAX_VALUE);
        for (String path : java.util.List.of(pricePath, policyPath)) {
            assertThat(request("GET", path, nonAdmin, null, null).statusCode()).isEqualTo(403);
            assertThat(request("GET", path, null, null, null).statusCode()).isEqualTo(401);
            for (String query : java.util.List.of("?limit=0", "?limit=101", "?offset=-1", "?offset=500001")) {
                assertThat(request("GET", path + query, admin, null, null).statusCode()).isEqualTo(422);
            }
        }
        assertThat(request("GET", "/api/v1/admin/billing/plans/UNKNOWN/prices/history", admin, null, null).statusCode()).isEqualTo(404);
        assertThat(publicationPreviewSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    @Test void adminSubscriptionLookupScopesCurrentOwnerMembershipsWithoutChangingPageCardinality() throws Exception {
        Actor admin = administrator(), reader = administrator(), owner = actor(), secondOwner = actor();
        Actor revoked = actor(), invited = actor(), manager = actor();
        UUID selected = merchant(owner), ownerless = merchant(owner), other = merchant(secondOwner), deleted = merchant(owner);
        member(selected, secondOwner, "OWNER");
        member(selected, revoked, "OWNER");
        member(selected, invited, "OWNER");
        member(selected, manager, "MANAGER");
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=? and user_id=?", selected, revoked.id());
        jdbc.update("update merchant_memberships set membership_status='INVITED' where merchant_profile_id=? and user_id=?", selected, invited.id());
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where merchant_profile_id=?", ownerless);
        for (UUID shop : java.util.List.of(selected, ownerless, other, deleted)) {
            jdbc.update("""
                insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,grace_until,created_at,updated_at)
                values (?,'SELLER_STANDARD','ACTIVE',now(),now()+interval '30 days',now()+interval '33 days',now(),now())
                """, shop);
        }
        jdbc.update("update merchant_profiles set deleted_at=now() where id=?", deleted);
        enableFixtureFinanceRoles(admin);
        assignFinanceRole(admin, reader, "READER");
        Map<String, String> before = publicationPreviewSnapshot();
        String path = "/api/v1/admin/billing/subscriptions";
        var response = request("GET", path + "?merchantId=" + selected + "&limit=1&offset=0", reader, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode entries = json.readTree(response.body());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).path("merchantId").asText()).isEqualTo(selected.toString());
        assertThat(entries.get(0).path("currentOwnerUserIds")).extracting(JsonNode::asText)
            .containsExactlyElementsOf(java.util.stream.Stream.of(owner.id(), secondOwner.id()).map(UUID::toString).sorted().toList());
        assertThat(entries.get(0).toString()).doesNotContain("email", "phone", "token");
        assertThat(json.readTree(request("GET", path + "?merchantId=" + selected + "&limit=1&offset=1", reader, null, null).body())).isEmpty();
        JsonNode ownerlessRows = json.readTree(request("GET", path + "?merchantId=" + ownerless, reader, null, null).body());
        assertThat(ownerlessRows).hasSize(1);
        assertThat(ownerlessRows.get(0).path("currentOwnerUserIds")).isEmpty();
        for (UUID absent : java.util.List.of(deleted, UUID.randomUUID())) {
            assertThat(json.readTree(request("GET", path + "?merchantId=" + absent, reader, null, null).body())).isEmpty();
        }
        assertThat(request("GET", path, admin, null, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", path, owner, null, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", path, null, null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", path + "?limit=101", reader, null, null).statusCode()).isEqualTo(422);
        assertThat(publicationPreviewSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(0);
    }

    private Map<String, String> publicationPreviewSnapshot() {
        Map<String, String> result = financialSnapshot();
        for (String table : java.util.List.of("billing_plans", "billing_plan_versions", "billing_policy_versions", "audit_logs", "outbox_events")) {
            result.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(entry) order by to_jsonb(entry)::text),'[]'::jsonb)::text from "
                + table + " entry", String.class));
        }
        return result;
    }

    private String createPricePlan(Actor admin) throws Exception {
        String code = "PRICE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var response = request("POST", "/api/v1/admin/billing/plans", admin, json.createObjectNode()
            .put("code", code).put("displayName", "Price version fixture").put("priceVnd", 10000)
            .put("cycleMonths", 1).put("effectiveAt", Instant.now().plusSeconds(2).toString()), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return code;
    }

    private JsonNode awaitPricePlan(String code, int revision) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (true) {
            var response = request("GET", "/api/v1/billing/plans", null, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            for (JsonNode plan : json.readTree(response.body())) {
                if (plan.path("code").asText().equals(code) && plan.path("revision").asInt() == revision) return plan;
            }
            assertThat(System.nanoTime()).as("scheduled plan revision becomes publicly effective").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    @Test void effectivePriceChangePreservesIssuedInvoiceAndRejectsOldUnconsumedQuote() throws Exception {
        Actor admin = administrator(), owner = actor();
        String code = createPricePlan(admin);
        JsonNode originalPlan = awaitPricePlan(code, 1);
        UUID originalShop = merchant(owner), newShop = merchant(owner);
        var originalRequest = quote(owner, originalShop, code);
        var issued = request("POST", base(originalShop) + "/checkout", owner, originalRequest, "price-original-" + UUID.randomUUID());
        assertThat(issued.statusCode()).isEqualTo(200);
        UUID originalInvoice = UUID.fromString(json.readTree(issued.body()).path("invoiceId").asText());
        String invoiceBefore = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, originalInvoice);
        String attemptBefore = jdbc.queryForObject("select to_jsonb(a)::text from billing_payment_attempts a where invoice_id=?", String.class, originalInvoice);
        ObjectNode stale = quote(owner, newShop, code);
        Instant effective = Instant.now().plusSeconds(4);
        var published = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
            json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1).put("effectiveAt", effective.toString()),
            null, Map.of("If-Match", "\"1\""));
        assertThat(published.statusCode()).isEqualTo(200);
        var beforeEffective = request("GET", "/api/v1/billing/plans", null, null, null);
        assertThat(json.readTree(beforeEffective.body()).findValuesAsText("id")).contains(originalPlan.path("id").asText());
        assertThat(awaitPricePlan(code, 2).path("priceVnd").asLong()).isEqualTo(15000);
        String key = "changed-price-" + UUID.randomUUID();
        var rejected = request("POST", base(newShop) + "/checkout", owner, stale, key);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).path("error").path("code").asText()).isEqualTo("BILLING_PRICE_INVALID");
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?", Long.class, newShop)).isZero();
        assertThat(provider.creations).hasValue(1);
        ObjectNode fresh = quote(owner, newShop, code);
        assertThat(fresh.path("expectedAmountVnd").asLong()).isEqualTo(15000);
        assertThat(fresh.path("expectedPlanRevision").asInt()).isEqualTo(2);
        var bought = request("POST", base(newShop) + "/checkout", owner, fresh, key);
        assertThat(bought.statusCode()).isEqualTo(200);
        assertThat(json.readTree(bought.body()).path("amountVnd").asLong()).isEqualTo(15000);
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, originalInvoice)).isEqualTo(invoiceBefore);
        assertThat(jdbc.queryForObject("select to_jsonb(a)::text from billing_payment_attempts a where invoice_id=?", String.class, originalInvoice)).isEqualTo(attemptBefore);
        assertThat(jdbc.queryForObject("select price_vnd from billing_plan_versions where id=?", Long.class,
            UUID.fromString(originalPlan.path("id").asText()))).isEqualTo(10000L);
        assertThat(provider.creations).hasValue(2);
    }

    @Test void billingOperationTimersObserveCommittedSettlementReplayAndRejectedRequests() throws Exception {
        Actor owner = actor();
        UUID shop = merchant(owner);
        long checkoutBefore = operationCount("checkout", "completed");
        long settledBefore = operationCount("webhook", "settled");
        long duplicateBefore = operationCount("webhook", "duplicate");
        long rejectedBefore = operationCount("webhook", "rejected");
        JsonNode invoice = purchase(owner, shop);
        settle(invoice);
        ObjectNode callback = provider.callback(invoice.path("orderCode").asLong());
        var replay = request("POST", "/api/v1/billing/webhooks/payos", null, callback, null);
        assertThat(json.readTree(replay.body()).path("status").asText()).isEqualTo("duplicate");
        callback.put("signature", "invalid-fixture-signature");
        assertThat(request("POST", "/api/v1/billing/webhooks/payos", null, callback, null).statusCode()).isEqualTo(401);
        ObjectNode changed = quote(owner, shop);
        changed.put("expectedAmountVnd", changed.path("expectedAmountVnd").asLong() + 1);
        long declinedBefore = operationCount("checkout", "rejected");
        assertThat(request("POST", base(shop) + "/checkout", owner, changed, "metrics-invalid-" + UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(request("GET", base(shop) + "/summary", owner, null, null).statusCode()).isEqualTo(200);
        assertThat(billingEligibility.merchantsAllowedToSell(java.util.Set.of(shop))).containsExactly(shop);
        assertThat(operationCount("checkout", "completed")).isEqualTo(checkoutBefore + 1);
        assertThat(operationCount("checkout", "rejected")).isEqualTo(declinedBefore + 1);
        assertThat(operationCount("webhook", "settled")).isEqualTo(settledBefore + 1);
        assertThat(operationCount("webhook", "duplicate")).isEqualTo(duplicateBefore + 1);
        assertThat(operationCount("webhook", "rejected")).isEqualTo(rejectedBefore + 1);
        assertThat(operationCount("entitlement", "observed")).isPositive();
        assertThat(operationCount("entitlement_batch", "observed")).isPositive();
        assertThat(provider.creations).hasValue(1);
    }

    private long operationCount(String operation, String outcome) {
        var timer = billingMetrics.find("covie.billing.operations").tags("operation", operation, "outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test void billingCachedReconciliationReviewSignalSurvivesFreshFailedChecksAndClearsAfterVerifiedRetry() throws Exception {
        Actor owner = actor();
        JsonNode invoice = purchase(owner, merchant(owner));
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        var candidate = reconciliation.pending(100).stream()
            .filter(pending -> pending.invoiceId().equals(invoiceId)).findFirst().orElseThrow();
        var before = financialSnapshot();
        provider.tamperNextLookupSignature.set(true);
        var failed = reconciliation.queryProvider(candidate);
        assertThat(failed.discrepancy()).isEqualTo("PROVIDER_FAILURE");
        reconciliation.record(failed);
        assertThat(jdbc.queryForObject("select extract(epoch from(now()-last_checked_at)) from billing_reconciliation_observations where invoice_id=?",
            Double.class, invoiceId)).isLessThan(5);
        billingDatabaseMetrics.refresh();
        var review = billingMetrics.find("covie.billing.reconciliation.review.required").gauge();
        assertThat(review).as("fresh failed provider checks must remain observable independently of check age").isNotNull();
        assertThat(review.value()).isEqualTo(jdbc.queryForObject("""
            select count(distinct i.id) from billing_invoices i
            join billing_payment_attempts a on a.invoice_id=i.id
            join billing_reconciliation_observations obs on obs.invoice_id=i.id
            where i.status='PENDING_PAYMENT' and a.provider='PAYOS' and i.payos_order_code is not null
              and obs.discrepancy<>'NONE'
            """, Double.class));
        double failedCount = review.value();
        assertThat(failedCount).isPositive();
        var verified = reconciliation.queryProvider(candidate);
        assertThat(verified.discrepancy()).isEqualTo("NONE");
        reconciliation.record(verified);
        billingDatabaseMetrics.refresh();
        assertThat(review.value()).isEqualTo(failedCount - 1);
        assertThat(financialSnapshot()).isEqualTo(before);
        assertThat(provider.creations).hasValue(1);
    }

    @Test void billingOperationalScrapeProvidesCachedFinancialSignalsWithoutScrapeSql() throws Exception {
        Actor owner = actor();
        UUID shop = merchant(owner);
        JsonNode invoice = purchase(owner, shop);
        UUID invoiceId = UUID.fromString(invoice.path("invoiceId").asText());
        jdbc.update("""
            insert into billing_reconciliation_observations(invoice_id,provider,provider_status,discrepancy,last_checked_at)
            values (?,'PAYOS','PENDING','NONE',now()-interval '10 minutes')
            """, invoiceId);
        billingDatabaseMetrics.refresh();
        String measured = UUID.randomUUID().toString();
        var scrape = requestWithHeaders("GET", "/actuator/prometheus", null, null, null,
            Map.of(vn.covie.billing.support.RequestSqlCounter.HEADER, measured));
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("covie_billing_invoices", "covie_billing_subscriptions",
            "covie_billing_reconciliation_pending", "covie_billing_database_last_success_unixtime",
            "covie_billing_reconciliation_enabled 0.0", "covie_billing_payment_creation_enabled 1.0");
        assertThat(vn.covie.billing.support.RequestSqlCounter.take(measured)).isZero();
        assertThat(billingMetrics.get("covie.billing.invoices").tag("status", "PENDING_PAYMENT").gauge().value())
            .isEqualTo(jdbc.queryForObject("select count(*) from billing_invoices where status='PENDING_PAYMENT'", Double.class));
        assertThat(billingMetrics.get("covie.billing.reconciliation.oldest.check.age.seconds").gauge().value()).isGreaterThanOrEqualTo(599);
        assertThat(scrape.body()).doesNotContain(shop.toString(), invoiceId.toString());
        settle(invoice);
        billingDatabaseMetrics.refresh();
        assertThat(billingMetrics.get("covie.billing.invoices").tag("status", "PAID").gauge().value())
            .isEqualTo(jdbc.queryForObject("select count(*) from billing_invoices where status='PAID'", Double.class));
        assertThat(billingMetrics.get("covie.billing.entitled.merchants").gauge().value())
            .isEqualTo(jdbc.queryForObject("select count(distinct merchant_id) from billing_entitlement_grants where revoked_at is null and valid_from<=now() and valid_until>now()", Double.class));
        assertThat(billingMetrics.get("covie.billing.database.last.success.unixtime").gauge().value()).isPositive();
        assertThat(billingMetrics.get("covie.billing.reconciliation.enabled").gauge().value()).isZero();
        assertThat(provider.creations).hasValue(1);
    }

    @Test void existingPaidSubscriberRenewsAtNotifiedEffectivePriceWithoutRewritingPaidHistory() throws Exception {
        Actor admin = administrator(), owner = actor();
        String code = createPricePlan(admin);
        awaitPricePlan(code, 1);
        UUID shop = merchant(owner);
        var issued = request("POST", base(shop) + "/checkout", owner, quote(owner, shop, code), "subscriber-first-" + UUID.randomUUID());
        assertThat(issued.statusCode()).isEqualTo(200);
        JsonNode first = json.readTree(issued.body());
        assertThat(first.path("amountVnd").asLong()).isEqualTo(10000);
        settle(first);
        UUID firstId = UUID.fromString(first.path("invoiceId").asText());
        Map<String, String> beforePublication = financialSnapshot();
        String oldInvoice = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, firstId);
        String oldAttempt = jdbc.queryForObject("select to_jsonb(a)::text from billing_payment_attempts a where invoice_id=?", String.class, firstId);
        String oldGrant = jdbc.queryForObject("select to_jsonb(g)::text from billing_entitlement_grants g where source_id=?", String.class, firstId);
        Instant paidThrough = jdbc.queryForObject("select period_end from merchant_subscriptions where merchant_id=?", Timestamp.class, shop).toInstant();
        var publication = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
            json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1)
                .put("effectiveAt", Instant.now().plusSeconds(5).toString()), null, Map.of("If-Match", "\"1\""));
        assertThat(publication.statusCode()).isEqualTo(200);
        UUID version = UUID.fromString(json.readTree(publication.body()).path("id").asText());
        UUID event = jdbc.queryForObject("select id from outbox_events where event_type='BILLING_PRICE_PUBLISHED' and aggregate_id=?", UUID.class, version);
        for (int processed = 0; processed < 1000; processed++) {
            if ("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event))) break;
            assertThat(outboxProcessor.processOne()).isTrue();
        }
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=? and user_id=?", Long.class, event, owner.id())).isEqualTo(1L);
        var inbox = request("GET", "/api/v1/me/notifications", owner, null, null);
        assertThat(inbox.statusCode()).isEqualTo(200);
        assertThat(json.readTree(inbox.body()).path("items").findValuesAsText("planVersionId")).contains(version.toString());
        assertThat(financialSnapshot()).isEqualTo(beforePublication);
        awaitPricePlan(code, 2);
        ObjectNode renewalQuote = quote(owner, shop, code);
        assertThat(renewalQuote.path("expectedAmountVnd").asLong()).isEqualTo(15000);
        assertThat(renewalQuote.path("expectedPlanRevision").asInt()).isEqualTo(2);
        var renewed = request("POST", base(shop) + "/checkout", owner, renewalQuote, "subscriber-renew-" + UUID.randomUUID());
        assertThat(renewed.statusCode()).isEqualTo(200);
        JsonNode renewal = json.readTree(renewed.body());
        UUID renewalId = UUID.fromString(renewal.path("invoiceId").asText());
        assertThat(renewal.path("amountVnd").asLong()).isEqualTo(15000);
        assertThat(jdbc.queryForObject("select plan_version_id from billing_invoices where id=?", UUID.class, renewalId)).isEqualTo(version);
        assertThat(jdbc.queryForObject("select period_start from billing_invoices where id=?", Timestamp.class, renewalId).toInstant()).isEqualTo(paidThrough);
        settle(renewal);
        assertThat(jdbc.queryForObject("select period_end from merchant_subscriptions where merchant_id=?", Timestamp.class, shop).toInstant())
            .isEqualTo(paidThrough.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant());
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, firstId)).isEqualTo(oldInvoice);
        assertThat(jdbc.queryForObject("select to_jsonb(a)::text from billing_payment_attempts a where invoice_id=?", String.class, firstId)).isEqualTo(oldAttempt);
        assertThat(jdbc.queryForObject("select to_jsonb(g)::text from billing_entitlement_grants g where source_id=?", String.class, firstId)).isEqualTo(oldGrant);
        assertThat(provider.creations).hasValue(2);
    }

    @Test void concurrentSameRevisionPricePublicationsWaitOnPostgresAndReturnOneConflict() throws Exception {
        Actor admin = administrator();
        String code = createPricePlan(admin);
        UUID published = racePublications(admin, "/api/v1/admin/billing/plans/" + code + "/prices",
            json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1)
                .put("effectiveAt", Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()), 1,
            "select code from billing_plans where code='" + code + "' for update", "%billing_plans%");
        assertThat(jdbc.queryForObject("select count(*) from billing_plan_versions where plan_code=?", Long.class, code)).isEqualTo(2L);
        assertPublicationEvents(published, "BILLING_PRICE_PUBLISHED");
    }

    @Test void concurrentSameRevisionPolicyPublicationsWaitOnPostgresAndReturnOneConflict() throws Exception {
        Actor admin = administrator();
        int revision = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        long count = jdbc.queryForObject("select count(*) from billing_policy_versions", Long.class);
        UUID published = racePublications(admin, "/api/v1/admin/billing/policy",
            json.createObjectNode().put("onboardingMode", "PAY_BEFORE_SELL").put("trialDays", 0).put("graceDays", 3)
                .put("effectiveAt", Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()), revision,
            "select id from billing_policy_versions order by revision asc limit 1 for update", "%billing_policy_versions%");
        assertThat(jdbc.queryForObject("select count(*) from billing_policy_versions", Long.class)).isEqualTo(count + 1);
        assertPublicationEvents(published, "BILLING_POLICY_PUBLISHED");
    }

    private UUID racePublications(Actor admin, String path, ObjectNode body, int revision, String lockSql, String queryPattern) throws Exception {
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
             var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.createStatement()) { statement.executeQuery(lockSql).close(); }
                var first = workers.submit(() -> requestWithHeaders("POST", path, admin, body, null, Map.of("If-Match", "\"" + revision + "\"")));
                var second = workers.submit(() -> requestWithHeaders("POST", path, admin, body, null, Map.of("If-Match", "\"" + revision + "\"")));
                long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                int waiting;
                do {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?", Integer.class, queryPattern);
                    if (waiting < 2) Thread.sleep(20);
                } while (waiting < 2 && System.nanoTime() < deadline);
                assertThat(waiting).as("both actual authenticated HTTP publications are waiting on PostgreSQL row locks").isEqualTo(2);
                blocker.commit();
                var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
                var winner = results.stream().filter(result -> result.statusCode() == 200).findFirst().orElseThrow();
                var loser = results.stream().filter(result -> result.statusCode() == 409).findFirst().orElseThrow();
                assertThat(json.readTree(loser.body()).path("error").path("code").asText()).isEqualTo("VALIDATION_FAILED");
                var invalidSchedule = requestWithHeaders("POST", path, admin, body, null,
                    Map.of("If-Match", "\"" + (revision + 1) + "\""));
                assertThat(invalidSchedule.statusCode()).isEqualTo(422);
                assertThat(json.readTree(invalidSchedule.body()).path("error").path("code").asText()).isEqualTo("VALIDATION_FAILED");
                return UUID.fromString(json.readTree(winner.body()).path("id").asText());
            } finally { blocker.rollback(); }
        }
    }

    private void assertPublicationEvents(UUID published, String action) {
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action=?", Long.class, published, action)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type=?", Long.class, published, action)).isEqualTo(1L);
    }

    @Test
    @EnabledIfSystemProperty(named = "covie.test.billing-browser", matches = "true")
    void actualSellerAndAdminBrowsersUseSignedProviderHttpAndPersistOneSettlementPerShop() throws Exception {
        Actor owner = actor(), initialAdmin = actor();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',?)",
            UUID.randomUUID(), initialAdmin.id(), Timestamp.from(Instant.now()));
        Actor admin = new Actor(initialAdmin.id(), sessions.issue(initialAdmin.id(),
            new DeviceMetadata("Billing Admin browser", "billing-browser-test")).accessToken().value());
        var shops = new java.util.ArrayList<Map<String, Object>>();
        for (int width : new int[]{360, 390, 1366}) shops.add(Map.of("width", width, "merchantId", merchant(owner).toString()));
        String scenario = System.getProperty("covie.test.billing-browser-scenario", "happy");
        Map<String, Object> adminReadFixture = new LinkedHashMap<>();
        Map<String, String> adminReadSnapshot = null;
        if (scenario.equals("admin-reads")) {
            String code = createPricePlan(admin);
            var publication = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
                json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 2)
                    .put("effectiveAt", Instant.now().plusSeconds(3600).toString()), null, Map.of("If-Match", "\"1\""));
            assertThat(publication.statusCode()).isEqualTo(200);
            assertThat(request("POST", "/api/v1/admin/billing/plans/" + code + "/retire", admin, null, null).statusCode()).isEqualTo(204);
            var readInvoices = new java.util.ArrayList<Map<String, Object>>();
            for (var shop : shops) {
                JsonNode invoice = purchase(owner, UUID.fromString(shop.get("merchantId").toString()));
                settle(invoice);
                readInvoices.add(Map.of("invoiceId", invoice.path("invoiceId").asText(), "merchantId", shop.get("merchantId"),
                    "width", shop.get("width"), "amountVnd", invoice.path("amountVnd").asLong()));
            }
            adminReadFixture.put("historyPlanCode", code);
            adminReadFixture.put("readInvoices", readInvoices);
            adminReadFixture.put("ownerUserId", owner.id().toString());
            adminReadSnapshot = publicationPreviewSnapshot();
        }
        Map<String, Object> priceNoticeFixture = new LinkedHashMap<>();
        Map<String, String> noticeFinancialSnapshot = null;
        if (scenario.equals("price-notice")) {
            String code = createPricePlan(admin);
            UUID shop = UUID.fromString(shops.getFirst().get("merchantId").toString());
            jdbc.update("""
                insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,created_at,updated_at)
                values (?,?,'ACTIVE',now(),now()+interval '30 days',now(),now())
                """, shop, code);
            noticeFinancialSnapshot = financialSnapshot();
            Instant effective = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            var published = requestWithHeaders("POST", "/api/v1/admin/billing/plans/" + code + "/prices", admin,
                json.createObjectNode().put("priceVnd", 15000).put("cycleMonths", 1).put("effectiveAt", effective.toString()),
                null, Map.of("If-Match", "\"1\""));
            assertThat(published.statusCode()).isEqualTo(200);
            UUID version = UUID.fromString(json.readTree(published.body()).path("id").asText());
            UUID event = jdbc.queryForObject("select id from outbox_events where event_type='BILLING_PRICE_PUBLISHED' and aggregate_id=?", UUID.class, version);
            for (int processed = 0; processed < 10000; processed++) {
                if ("PUBLISHED".equals(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event))) break;
                assertThat(outboxProcessor.processOne()).isTrue();
            }
            assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=? and user_id=?", Long.class, event, owner.id())).isEqualTo(1L);
            priceNoticeFixture.put("noticePlanCode", code);
            priceNoticeFixture.put("noticeVersionId", version.toString());
        }
        Map<String, String> reconciliationActors = new LinkedHashMap<>();
        if (scenario.equals("manual-reconciliation")) {
            Actor operator = administrator(), reader = administrator();
            enableFixtureFinanceRoles(admin);
            assignFinanceRole(admin, operator, "OPERATOR");
            assignFinanceRole(admin, reader, "READER");
            reconciliationActors.put("operatorToken", operator.token());
            reconciliationActors.put("readerToken", reader.token());
        }
        int calendarYear = futureCalendarYear(false);
        if (scenario.equals("calendar")) {
            for (var shop : shops) {
                jdbc.update("""
                    insert into billing_entitlement_grants(id,merchant_id,source_type,source_id,valid_from,valid_until,reason,created_at)
                    values (?,?,'ADMIN',?,now()-interval '1 day',?,'Calendar browser fixture',now())
                    """, UUID.randomUUID(), UUID.fromString(shop.get("merchantId").toString()), UUID.randomUUID(),
                    Timestamp.from(java.time.LocalDateTime.of(calendarYear, 1, 30, 18, 30).toInstant(java.time.ZoneOffset.UTC)));
            }
        }
        if (scenario.equals("recovery")) {
            UUID expiringMerchant = UUID.fromString(shops.get(0).get("merchantId").toString());
            provider.registerAction("expire-quotes", () -> jdbc.update("""
                update billing_quotes set created_at=created_at-interval '1 day',expires_at=expires_at-interval '1 day'
                where merchant_id=? and consumed_by_idempotency_key is null
                """, expiringMerchant));
            provider.registerAction("lose-response", () -> provider.loseNextCreateResponse.set(true));
            java.util.Set<UUID> merchantIds = shops.stream().map(shop -> UUID.fromString(shop.get("merchantId").toString()))
                .collect(java.util.stream.Collectors.toSet());
            provider.registerAction("reconcile", () -> reconciliation.pending(100).stream()
                .filter(candidate -> merchantIds.contains(jdbc.queryForObject("select merchant_id from billing_invoices where id=?",
                    UUID.class, candidate.invoiceId())))
                .forEach(candidate -> reconciliation.record(reconciliation.queryProvider(candidate))));
        }
        try (var available = new ServerSocket(3000)) { assertThat(available.getLocalPort()).isEqualTo(3000); }
        Path web = Path.of("../web").toAbsolutePath().normalize();
        Path output = Path.of("../../.local/billing-browser-" + System.currentTimeMillis()).toAbsolutePath().normalize();
        Files.createDirectories(output);
        var launcher = new ProcessBuilder(System.getProperty("covie.test.node", "node"),
            web.resolve("node_modules/next/dist/bin/next").toString(), "start", "-p", "3000", "-H", "127.0.0.1")
            .directory(web.toFile());
        launcher.environment().keySet().removeIf(key -> key.startsWith("PAYOS_") || key.equals("COVIE_BILLING_RELEASE_APPROVED")
            || key.equals("REDIS_URL"));
        launcher.environment().put("API_INTERNAL_BASE_URL", "http://localhost:" + port);
        launcher.environment().put("COVIE_PUBLIC_URL", "http://localhost:3000");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process next = launcher.start();
        boolean completed = false;
        try {
            HttpClient readiness = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
            boolean ready = false;
            for (int attempt = 0; attempt < 60 && next.isAlive() && !ready; attempt++) {
                try { ready = readiness.send(HttpRequest.newBuilder(URI.create("http://localhost:3000/login"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200; }
                catch (Exception ignored) { }
                if (!ready) Thread.sleep(250);
            }
            assertThat(ready).as("owned built Next server started").isTrue();
            String python = System.getProperty("covie.test.python",
                Path.of("../../.local/billing-browser-venv/Scripts/python.exe").toAbsolutePath().normalize().toString());
            String driverScript = scenario.equals("admin-reads") ? "billing-local-admin-reads.py"
                : scenario.equals("pricing") ? "billing-local-pricing.py"
                : scenario.equals("price-notice") ? "billing-local-price-notice.py"
                : scenario.equals("manual-reconciliation") ? "billing-local-reconciliation.py"
                : scenario.equals("recovery") ? "billing-local-recovery.py" : "billing-local-acceptance.py";
            var driver = new ProcessBuilder(python, web.resolve("e2e/" + driverScript).toString()).directory(web.toFile());
            driver.environment().put("COVIE_BILLING_BROWSER_TEST", "1");
            driver.environment().put("PYTHONIOENCODING", "utf-8");
            driver.redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile());
            Process browser = driver.start();
            try {
                Map<String, Object> fixture = new LinkedHashMap<>(Map.of("origin", "http://localhost:3000", "api", "http://localhost:" + port,
                    "provider", provider.origin(), "providerControl", provider.controlToken(),
                    "ownerToken", owner.token(), "adminToken", admin.token(), "shops", shops,
                    "planCode", "BROWSER_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "output", output.toString(),
                    "scenario", scenario));
                fixture.put("calendarYear", calendarYear);
                fixture.putAll(reconciliationActors);
                fixture.putAll(priceNoticeFixture);
                fixture.putAll(adminReadFixture);
                try (var input = browser.getOutputStream()) { input.write(json.writeValueAsBytes(fixture)); }
                assertThat(browser.waitFor(240, TimeUnit.SECONDS)).as("native browser fixture terminates").isTrue();
                assertThat(browser.exitValue()).as("inspect owned browser.log without exposing fixture stdin").isZero();
            } finally { if (browser.isAlive()) browser.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
            JsonNode result = json.readTree(Files.readString(output.resolve("result.json")));
            assertThat(result.path("completed").asBoolean()).isTrue();
            assertThat(result.path("invoices").size()).isEqualTo(scenario.equals("price-notice") ? 0 : 3);
            if (scenario.equals("admin-reads")) {
                assertThat(publicationPreviewSnapshot()).isEqualTo(adminReadSnapshot);
                assertThat(result.path("pageErrors").asInt()).isZero();
                assertThat(result.path("mutations").asInt()).isZero();
            }
            if (scenario.equals("price-notice")) {
                assertThat(result.path("screens").size()).isEqualTo(3);
                assertThat(result.path("pageErrors").asInt()).isZero();
                assertThat(financialSnapshot()).isEqualTo(noticeFinancialSnapshot);
            }
            if (scenario.equals("manual-reconciliation")) {
                assertThat(result.path("exports").size()).isEqualTo(3);
                var widths = new java.util.ArrayList<Integer>();
                for (JsonNode exported : result.path("exports")) {
                    int width = exported.path("width").asInt();
                    widths.add(width);
                    assertThat(exported.path("keyboardDownload").asBoolean()).isTrue();
                    assertThat(exported.path("deniedRoleHasNoExport").asBoolean()).isTrue();
                    assertThat(exported.path("rows").asInt()).isBetween(1, 20);
                    assertThat(exported.path("filename").asText()).isEqualTo("invoice-export-reader-" + width + ".csv");
                    byte[] downloaded = Files.readAllBytes(output.resolve("invoice-export-reader-" + width + ".csv"));
                    assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(downloaded)))
                        .isEqualTo(exported.path("sha256").asText());
                }
                assertThat(widths).containsExactlyInAnyOrder(360, 390, 1366);
            }
            for (JsonNode invoice : result.path("invoices")) {
                UUID id = UUID.fromString(invoice.path("invoiceId").asText());
                assertThat(jdbc.queryForObject("select status from billing_invoices where id=?", String.class, id)).isEqualTo("PAID");
                assertThat(jdbc.queryForObject("select amount_vnd from billing_invoices where id=?", Long.class, id))
                    .isEqualTo(scenario.equals("admin-reads") ? invoice.path("amountVnd").asLong() : 15000L);
                assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=?", Long.class, id)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=? and status='SUCCEEDED'", Long.class, id)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'", Long.class, id)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'", Long.class, id)).isEqualTo(1L);
                if (scenario.equals("manual-reconciliation")) {
                    assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_RECONCILIATION_REQUESTED'", Long.class, id)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_RECONCILIATION_COMPLETED'", Long.class, id)).isEqualTo(1L);
                }
            }
            assertThat(provider.creations).hasValue(scenario.equals("price-notice") ? 0 : 3);
            if (Boolean.getBoolean("covie.test.billing-ads-browser")) {
                UUID merchant = UUID.fromString(shops.get(0).get("merchantId").toString());
                enableFixtureBilling(owner);
                JsonNode campaign = fixtureApprovedAd(owner, admin, merchant, "/merchants/" + merchant);
                UUID campaignId = UUID.fromString(campaign.path("id").asText());
                ObjectNode payload = (ObjectNode) json.readTree(jdbc.queryForObject("select payload::text from ad_campaigns where id=?", String.class, campaignId));
                var promoted = requestWithHeaders("PATCH", "/api/v1/admin/ads/" + campaignId, admin,
                    payload.put("placement", "HOME_HERO"), null,
                    Map.of("If-Match", vn.covie.shared.concurrency.VersionPrecondition.format(2)));
                assertThat(promoted.statusCode()).as(promoted.body()).isEqualTo(200);
                String stored = jdbc.queryForObject("select to_jsonb(c)::text from ad_campaigns c where id=?", String.class, campaignId);
                var afterFixture = new java.util.concurrent.atomic.AtomicReference<Map<String, String>>();
                provider.registerAction("ads-short-term", () -> {
                    Instant until = Instant.now().plusSeconds(12);
                    fixtureTerm(merchant, until, until.plus(Duration.ofDays(3)), "ACTIVE");
                    afterFixture.set(financialSnapshot());
                });
                provider.registerAction("ads-renew-term", () -> {
                    Instant until = Instant.now().plusSeconds(3600);
                    fixtureTerm(merchant, until, until.plus(Duration.ofDays(3)), "ACTIVE");
                    afterFixture.set(financialSnapshot());
                });
                int lookups = provider.lookups.get();
                var adsDriver = new ProcessBuilder(python, web.resolve("e2e/billing-local-ads-expiry.py").toString()).directory(web.toFile());
                adsDriver.environment().put("COVIE_BILLING_BROWSER_TEST", "1");
                adsDriver.environment().put("PYTHONIOENCODING", "utf-8");
                adsDriver.redirectErrorStream(true).redirectOutput(output.resolve("ads-browser.log").toFile());
                Process adsBrowser = adsDriver.start();
                try {
                    try (var input = adsBrowser.getOutputStream()) {
                        input.write(json.writeValueAsBytes(Map.of("origin", "http://localhost:3000", "api", "http://localhost:" + port,
                            "provider", provider.origin(), "providerControl", provider.controlToken(),
                            "merchantId", merchant.toString(), "output", output.toString())));
                    }
                    assertThat(adsBrowser.waitFor(90, TimeUnit.SECONDS)).isTrue();
                    assertThat(adsBrowser.exitValue()).as("inspect owned ads-browser.log").isZero();
                } finally { if (adsBrowser.isAlive()) adsBrowser.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
                JsonNode adsResult = json.readTree(Files.readString(output.resolve("ads-expiry-result.json")));
                assertThat(adsResult.path("completed").asBoolean()).isTrue();
                assertThat(adsResult.path("widths").size()).isEqualTo(3);
                assertThat(adsResult.path("pageErrors").asInt()).isZero();
                assertThat(financialSnapshot()).isEqualTo(afterFixture.get());
                assertThat(jdbc.queryForObject("select to_jsonb(c)::text from ad_campaigns c where id=?", String.class, campaignId)).isEqualTo(stored);
                assertThat(provider.creations).hasValue(3);
                assertThat(provider.lookups).hasValue(lookups);
            }
            completed = true;
        } finally {
            if (next.isAlive()) next.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            Files.writeString(Path.of("../../.local/billing-browser-latest.json"), json.writeValueAsString(
                Map.of("output", output.toString(), "completed", completed)));
        }
    }
}
