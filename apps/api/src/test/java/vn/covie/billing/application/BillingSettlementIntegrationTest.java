package vn.covie.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.billing.payos.PayosSignature;
import vn.covie.billing.reconciliation.BillingReconciliationService;
import vn.covie.shared.security.CurrentActor;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false",
    "PAYOS_PAYMENT_ENABLED=false", "COVIE_BILLING_RELEASE_APPROVED=false",
    "COVIE_BILLING_RECONCILE_ENABLED=false"
})
@ActiveProfiles("test")
@Import(BillingSettlementIntegrationTest.ProviderConfiguration.class)
class BillingSettlementIntegrationTest {
    private static final String CHECKSUM = "billing-fixture-checksum-only";
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("billing_settlement_test").withUsername("billing")
        .withPassword("billing-test-only");
    static { POSTGRES.start(); }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll static void cleanup() { POSTGRES.stop(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean @Primary FixtureProvider fixtureProvider(ObjectMapper mapper, PayosSignature signatures) {
            return new FixtureProvider(mapper, signatures);
        }
    }

    static class FixtureProvider extends PayosGateway {
        final Map<Long, JsonNode> responses = new ConcurrentHashMap<>();
        final AtomicBoolean loseNextResponse = new AtomicBoolean();
        volatile BiConsumer<Long, Long> beforeLinkReturn;
        FixtureProvider(ObjectMapper mapper, PayosSignature signatures) {
            super("fixture-client", "fixture-api", CHECKSUM, "https://billing.invalid", false, signatures, mapper);
        }
        @Override public boolean isEnabled() { return true; }
        @Override public PaymentLink createPaymentLink(UUID merchantId, long orderCode, long amount,
                String description, long expiredAt) {
            if (beforeLinkReturn != null) beforeLinkReturn.accept(orderCode, amount);
            if (loseNextResponse.getAndSet(false)) throw new ProviderUnavailableException("Fixture response lost after reservation");
            return new PaymentLink(orderCode, linkId(orderCode), "https://payment.invalid/" + orderCode, "fixture-qr");
        }
        @Override public JsonNode lookup(long orderCode) {
            JsonNode response = responses.get(orderCode);
            if (response == null) throw new ProviderUnavailableException("Fixture response unavailable");
            return response;
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired ObjectMapper json;
    @Autowired BillingCheckoutService checkout;
    @Autowired BillingWebhookService webhook;
    @Autowired BillingReconciliationService reconciliation;
    @Autowired FixtureProvider provider;
    @MockitoBean CurrentActorProvider actors;
    @LocalServerPort int port;
    private final AtomicReference<UUID> actor = new AtomicReference<>();

    @BeforeEach void actorLookup() {
        provider.beforeLinkReturn = null;
        provider.loseNextResponse.set(false);
        when(actors.currentActor()).thenAnswer(invocation -> new CurrentActor(actor.get(), Set.of(), UUID.randomUUID()));
    }

    private UUID merchant() {
        UUID owner = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Billing fixture','ACTIVE',?,?)",
            owner, now, now);
        jdbc.update("""
            insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
            values (?,'Billing fixture',?,'ACTIVE','APPROVED',?,?,0)
            """, merchant, "billing-" + merchant, now, now);
        jdbc.update("""
            insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version)
            values (?,?,?,'OWNER','ACTIVE',?,?,0)
            """, UUID.randomUUID(), merchant, owner, now, now);
        actor.set(owner);
        return merchant;
    }

    private BillingCheckoutService.CheckoutResult purchase(UUID merchant) {
        var quote = checkout.quote(merchant, "SELLER_STANDARD", null);
        return checkout.create(merchant, "settle-" + UUID.randomUUID(),
            new BillingCheckoutService.CheckoutRequest("SELLER_STANDARD", null,
                quote.amountDueVnd(), quote.planRevision(), quote.quoteId()));
    }

    private static String linkId(long orderCode) { return String.format("%032x", orderCode); }

    private ObjectNode paymentData(BillingCheckoutService.CheckoutResult invoice, String reference) {
        return json.createObjectNode().put("orderCode", invoice.orderCode()).put("amount", invoice.amountVnd())
            .put("currency", "VND").put("paymentLinkId", linkId(invoice.orderCode()))
            .put("reference", reference).put("code", "00").put("description", "Fixture payment")
            .put("transactionDateTime", "2026-10-17 20:00:00");
    }

    private ObjectNode signed(ObjectNode data) throws Exception {
        TreeMap<String, String> fields = new TreeMap<>();
        data.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue().isValueNode()
            ? entry.getValue().asText() : entry.getValue().toString()));
        String canonical = fields.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CHECKSUM.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return json.createObjectNode().put("code", "00").put("success", true)
            .put("signature", HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))))
            .set("data", data);
    }

    private ObjectNode providerPaid(BillingCheckoutService.CheckoutResult invoice, String reference) throws Exception {
        ObjectNode data = json.createObjectNode().put("orderCode", invoice.orderCode())
            .put("amount", invoice.amountVnd()).put("amountPaid", invoice.amountVnd())
            .put("id", linkId(invoice.orderCode())).put("status", "PAID");
        data.putArray("transactions").addObject().put("reference", reference).put("amount", invoice.amountVnd());
        return signed(data);
    }

    private HttpResponse<String> callback(ObjectNode payload) throws Exception {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/billing/webhooks/payos"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private void paidExactlyOnce(UUID merchant, BillingCheckoutService.CheckoutResult invoice) {
        assertThat(jdbc.queryForObject("select status from billing_invoices where id=?", String.class, invoice.invoiceId()))
            .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from billing_payment_attempts where invoice_id=? and status='SUCCEEDED'",
            Long.class, invoice.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where source_id=? and source_type='PAID_INVOICE'",
            Long.class, invoice.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='SELLER_SUBSCRIPTION_PAYMENT_SETTLED'",
            Long.class, invoice.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='BILLING_PAYMENT_SETTLED'",
            Long.class, invoice.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select period_end from merchant_subscriptions where merchant_id=?",
            Timestamp.class, merchant)).isEqualTo(jdbc.queryForObject("select period_end from billing_invoices where id=?",
                Timestamp.class, invoice.invoiceId()));
    }

    @Test void signedHttpCallbackDuplicateAndReorderedReferenceDoNotExtendTwice() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        var payload = signed(paymentData(invoice, "reference-" + UUID.randomUUID()));
        var first = callback(payload);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(json.readTree(first.body()).path("status").asText()).isEqualTo("settled");
        assertThat(first.headers().firstValue("cache-control")).contains("no-store");
        assertThat(json.readTree(callback(payload).body()).path("status").asText()).isEqualTo("duplicate");
        assertThat(webhook.receive(signed(paymentData(invoice, "reordered-" + UUID.randomUUID())).toString()))
            .isEqualTo("duplicate");
        paidExactlyOnce(merchant, invoice);
        assertThat(jdbc.queryForObject("select count(*) from billing_webhook_inbox where processing_status<>'DONE'",
            Long.class)).isZero();
    }

    @Test void invalidSignatureTamperedTimestampAmountCurrencyAndLinkCannotMutateInvoice() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        String before = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice.invoiceId());
        var forged = signed(paymentData(invoice, "forged-" + UUID.randomUUID()));
        ((ObjectNode) forged.path("data")).put("transactionDateTime", "2000-01-01 00:00:00");
        assertThat(callback(forged).statusCode()).isEqualTo(401);
        for (ObjectNode changed : java.util.List.of(
                paymentData(invoice, "under").put("amount", invoice.amountVnd() - 1),
                paymentData(invoice, "over").put("amount", invoice.amountVnd() + 1),
                paymentData(invoice, "currency").put("currency", "USD"),
                paymentData(invoice, "link").put("paymentLinkId", "foreign-link"))) {
            assertThat(callback(signed(changed)).statusCode()).isIn(409, 422);
        }
        assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice.invoiceId()))
            .isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, merchant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_webhook_inbox where provider_event_id in ('ref:under','ref:over','ref:currency','ref:link')",
            Long.class)).isZero();
    }

    @Test void lostWebhookRecoversFromSignedProviderResponseThenLateCallbackIsDuplicate() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        String reference = "recovery-" + UUID.randomUUID();
        provider.responses.put(invoice.orderCode(), providerPaid(invoice, reference));
        var candidate = new BillingReconciliationService.Candidate(invoice.invoiceId(), invoice.orderCode(),
            invoice.amountVnd(), linkId(invoice.orderCode()));
        var result = reconciliation.queryProvider(candidate);
        assertThat(result.providerStatus()).isEqualTo("PAID");
        assertThat(result.discrepancy()).isEqualTo("NONE");
        assertThat(webhook.receive(signed(paymentData(invoice, reference)).toString())).isEqualTo("duplicate");
        paidExactlyOnce(merchant, invoice);
    }

    @Test void concurrentWebhookAndReconciliationWaitOnTheSameInvoiceAndSettleOnce() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        String reference = "race-" + UUID.randomUUID();
        String payload = signed(paymentData(invoice, reference)).toString();
        provider.responses.put(invoice.orderCode(), providerPaid(invoice, reference));
        var candidate = new BillingReconciliationService.Candidate(invoice.invoiceId(), invoice.orderCode(),
            invoice.amountVnd(), linkId(invoice.orderCode()));
        try (var blocker = datasource.getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("select id from billing_invoices where id=? for update")) {
                statement.setObject(1, invoice.invoiceId());
                statement.executeQuery().close();
            }
            var first = workers.submit(() -> webhook.receive(payload));
            var second = workers.submit(() -> reconciliation.queryProvider(candidate));
            try {
                long waiting = 0;
                for (int attempt = 0; attempt < 100 && waiting < 2; attempt++) {
                    waiting = jdbc.queryForObject("""
                        select count(*) from pg_stat_activity where datname=current_database()
                        and wait_event_type='Lock' and query like '%for update of i,a%'
                        """, Long.class);
                    if (waiting < 2) Thread.sleep(50);
                }
                assertThat(waiting).as("both settlement transactions reached the held invoice lock").isEqualTo(2);
            } finally { blocker.commit(); }
            assertThat(first.get(15, TimeUnit.SECONDS)).isIn("settled", "duplicate");
            assertThat(second.get(15, TimeUnit.SECONDS).discrepancy()).isEqualTo("NONE");
        }
        paidExactlyOnce(merchant, invoice);
    }

    @Test void databaseFailureAtOutboxCommitRollsBackSettlementAndSignedRetryRecovers() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        String payload = signed(paymentData(invoice, "rollback-" + UUID.randomUUID())).toString();
        String before = jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice.invoiceId());
        jdbc.execute("""
            create function fixture_reject_billing_outbox() returns trigger language plpgsql as $$
            begin raise exception 'Fixture outbox commit failure'; return new; end; $$;
            create trigger fixture_reject_billing_outbox before insert on outbox_events
            for each row execute function fixture_reject_billing_outbox();
            """);
        try {
            assertThrows(RuntimeException.class, () -> webhook.receive(payload));
            assertThat(jdbc.queryForObject("select to_jsonb(i)::text from billing_invoices i where id=?", String.class, invoice.invoiceId()))
                .isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, merchant)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from merchant_subscriptions where merchant_id=?", Long.class, merchant)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=?", Long.class, invoice.invoiceId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from billing_webhook_inbox where provider_event_id=?", Long.class,
                "ref:" + json.readTree(payload).path("data").path("reference").asText())).isZero();
        } finally {
            jdbc.execute("drop trigger fixture_reject_billing_outbox on outbox_events; drop function fixture_reject_billing_outbox();");
        }
        assertThat(webhook.receive(payload)).isEqualTo("settled");
        paidExactlyOnce(merchant, invoice);
    }

    @Test void earlyRenewalStartsAtPaidThroughRatherThanChargingAnOverlappingPeriod() throws Exception {
        UUID merchant = merchant();
        var first = purchase(merchant);
        assertThat(webhook.receive(signed(paymentData(first, "first-" + UUID.randomUUID())).toString())).isEqualTo("settled");
        Timestamp paidThrough = jdbc.queryForObject("select period_end from billing_invoices where id=?", Timestamp.class, first.invoiceId());
        var renewal = purchase(merchant);
        assertThat(jdbc.queryForObject("select period_start from billing_invoices where id=?", Timestamp.class, renewal.invoiceId()))
            .isEqualTo(paidThrough);
        assertThat(webhook.receive(signed(paymentData(renewal, "renewal-" + UUID.randomUUID())).toString())).isEqualTo("settled");
        assertThat(jdbc.queryForObject("select count(*) from billing_entitlement_grants where merchant_id=?", Long.class, merchant)).isEqualTo(2L);
        paidExactlyOnce(merchant, renewal);
    }

    @Test void successfulSettlementHasOnePrivacyMinimalAuditRecordEvenAfterReplay() throws Exception {
        UUID merchant = merchant();
        var invoice = purchase(merchant);
        String payload = signed(paymentData(invoice, "audited-" + UUID.randomUUID())).toString();
        assertThat(webhook.receive(payload)).isEqualTo("settled");
        assertThat(webhook.receive(payload)).isEqualTo("duplicate");
        paidExactlyOnce(merchant, invoice);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_type='BILLING_INVOICE' and entity_id=?",
            Long.class, invoice.invoiceId())).isEqualTo(1L);
        JsonNode metadata = json.readTree(jdbc.queryForObject("select after_snapshot::text from audit_logs where entity_type='BILLING_INVOICE' and entity_id=?",
            String.class, invoice.invoiceId()));
        assertThat(metadata.path("merchantId").asText()).isEqualTo(merchant.toString());
        assertThat(metadata.toString()).doesNotContain(CHECKSUM, "signature", "reference", "transactionDateTime", "accountNumber");
    }

    @Test void callbackCanSettleBeforeTheCreateLinkResponseIsPersisted() throws Exception {
        UUID merchant = merchant();
        provider.beforeLinkReturn = (orderCode, amount) -> {
            UUID invoiceId = jdbc.queryForObject("select id from billing_invoices where payos_order_code=?", UUID.class, orderCode);
            var reserved = new BillingCheckoutService.CheckoutResult(invoiceId, orderCode, amount,
                null, null, Instant.now(), "PENDING_PAYMENT");
            try {
                assertThat(callback(signed(paymentData(reserved, "early-" + UUID.randomUUID()))).statusCode()).isEqualTo(200);
            } catch (Exception failure) { throw new IllegalStateException("Fixture early callback failed", failure); }
        };
        var result = purchase(merchant);
        assertThat(result.invoiceStatus()).isEqualTo("PAID");
        assertThat(result.checkoutUrl()).startsWith("https://payment.invalid/");
        paidExactlyOnce(merchant, result);
    }

    @Test void lostCreateResponseLeavesAnUnboundAttemptThatSignedReconciliationCanRecover() throws Exception {
        UUID merchant = merchant();
        provider.loseNextResponse.set(true);
        var unavailable = assertThrows(vn.covie.shared.web.ApiException.class, () -> purchase(merchant));
        assertThat(unavailable.code()).isEqualTo(vn.covie.shared.web.ErrorCode.BILLING_PAYMENT_UNAVAILABLE);
        assertThat(unavailable.status().value()).isEqualTo(503);
        var reserved = jdbc.queryForObject("""
            select id,payos_order_code,amount_vnd,expires_at,status from billing_invoices where merchant_id=?
            """, (row, index) -> new BillingCheckoutService.CheckoutResult(row.getObject("id", UUID.class),
                row.getLong("payos_order_code"), row.getLong("amount_vnd"), null, null,
                row.getTimestamp("expires_at").toInstant(), row.getString("status")), merchant);
        assertThat(jdbc.queryForObject("select provider_payment_link_id from billing_payment_attempts where invoice_id=?",
            String.class, reserved.invoiceId())).isNull();
        provider.responses.put(reserved.orderCode(), providerPaid(reserved, "lost-create-" + UUID.randomUUID()));
        var recovered = reconciliation.queryProvider(new BillingReconciliationService.Candidate(reserved.invoiceId(),
            reserved.orderCode(), reserved.amountVnd(), null));
        assertThat(recovered.discrepancy()).isEqualTo("NONE");
        assertThat(jdbc.queryForObject("select provider_payment_link_id from billing_payment_attempts where invoice_id=?",
            String.class, reserved.invoiceId())).isEqualTo(linkId(reserved.orderCode()));
        paidExactlyOnce(merchant, reserved);
    }
}
