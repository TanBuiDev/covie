package vn.covie.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

class BillingCheckoutReplayIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("billing_checkout_replay").withUsername("checkout")
        .withPassword("checkout-test-only");
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactionManager;

    @BeforeAll static void prepare() {
        POSTGRES.start();
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(datasource);
        transactionManager = new DataSourceTransactionManager(datasource);
    }

    @AfterAll static void cleanup() { POSTGRES.stop(); }

    private record Fixture(UUID merchant, BillingCheckoutService checkout, AtomicInteger providerCalls) {}

    private Fixture fixture(boolean providerTimeout) {
        UUID merchant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into merchant_profiles
              (id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
            values (?,'Checkout replay fixture',?,'ACTIVE','APPROVED',?,?,0)
            """, merchant, "checkout-" + merchant, Timestamp.from(now), Timestamp.from(now));
        var actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(owner, Set.of(), UUID.randomUUID()));
        var memberships = mock(MerchantMembershipRepository.class);
        when(memberships.findActive(merchant, owner)).thenReturn(Optional.of(
            MerchantMembershipEntity.active(UUID.randomUUID(), merchant, owner, MerchantRole.OWNER, now)));
        var settings = mock(SystemSettingResolver.class);
        when(settings.integer(anyString(), any())).thenReturn(100);
        var admins = mock(AdminAccessPolicy.class);
        var audit = mock(AuditLogWriter.class);
        var requestIds = mock(RequestIdAccessor.class);
        var catalog = new BillingCatalogService(jdbc, Instant::now, admins, audit, requestIds,
            mock(OutboxEventWriter.class));
        var vouchers = new BillingVoucherService(jdbc, Instant::now, admins, audit, requestIds);
        var provider = mock(PayosGateway.class);
        when(provider.isEnabled()).thenReturn(true);
        var calls = new AtomicInteger();
        when(provider.createPaymentLink(any(), anyLong(), anyLong(), anyString(), anyLong()))
            .thenAnswer(invocation -> {
                calls.incrementAndGet();
                assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?",
                    Long.class, merchant)).isEqualTo(1L);
                if (providerTimeout) throw new IllegalStateException("Test provider response lost");
                long orderCode = invocation.getArgument(1);
                return new PayosGateway.PaymentLink(orderCode, "fixture-link-" + orderCode,
                    "https://payment.invalid/" + orderCode, "fixture-qr");
            });
        return new Fixture(merchant, new BillingCheckoutService(jdbc, new ObjectMapper(), Instant::now,
            catalog, provider, memberships, actors, transactionManager, vouchers, settings), calls);
    }

    private BillingCheckoutService.CheckoutRequest request(Fixture fixture) {
        var quote = new TransactionTemplate(transactionManager).execute(transaction ->
            fixture.checkout().quote(fixture.merchant(), "SELLER_STANDARD", null));
        return new BillingCheckoutService.CheckoutRequest("SELLER_STANDARD", null,
            quote.amountDueVnd(), quote.planRevision(), quote.quoteId());
    }

    private void oneReservation(Fixture fixture) {
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where merchant_id=?",
            Long.class, fixture.merchant())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
            select count(*) from billing_payment_attempts a join billing_invoices i on i.id=a.invoice_id
            where i.merchant_id=?
            """, Long.class, fixture.merchant())).isEqualTo(1L);
        assertThat(fixture.providerCalls()).hasValue(1);
    }

    @Test void consumedAndExpiredQuoteReplaysTheOriginalPayment() {
        var fixture = fixture(false);
        var request = request(fixture);
        String key = "replay-" + UUID.randomUUID();
        var original = fixture.checkout().create(fixture.merchant(), key, request);
        jdbc.update("update billing_quotes set created_at=created_at-interval '1 day', expires_at=expires_at-interval '1 day' where id=?",
            request.quoteId());
        var replay = fixture.checkout().create(fixture.merchant(), key, request);
        assertThat(replay).usingRecursiveComparison().ignoringFields("expiresAt").isEqualTo(original);
        assertThat(java.time.Duration.between(original.expiresAt(), replay.expiresAt()).abs())
            .isLessThan(java.time.Duration.ofNanos(1000));
        assertThat(jdbc.queryForObject("select consumed_by_idempotency_key from billing_quotes where id=?",
            String.class, request.quoteId())).isEqualTo(key);
        oneReservation(fixture);
    }

    @Test void sameKeyWithChangedQuotedRequestConflictsWithoutAnotherProviderCall() {
        var fixture = fixture(false);
        var original = request(fixture);
        var anotherQuote = request(fixture);
        String key = "changed-" + UUID.randomUUID();
        fixture.checkout().create(fixture.merchant(), key, original);
        var changed = java.util.List.of(anotherQuote,
            new BillingCheckoutService.CheckoutRequest(original.planCode(), null,
                original.expectedAmountVnd() + 1, original.expectedPlanRevision(), original.quoteId()),
            new BillingCheckoutService.CheckoutRequest(original.planCode(), null,
                original.expectedAmountVnd(), original.expectedPlanRevision() + 1, original.quoteId()));
        for (var request : changed) {
            var failure = assertThrows(ApiException.class,
                () -> fixture.checkout().create(fixture.merchant(), key, request));
            assertThat(failure.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST);
        }
        oneReservation(fixture);
    }

    @Test void lostProviderResponseLeavesOneCommittedReservationAndRetryDoesNotPostAgain() {
        var fixture = fixture(true);
        var request = request(fixture);
        String key = "timeout-" + UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> fixture.checkout().create(fixture.merchant(), key, request));
        var failure = assertThrows(ApiException.class,
            () -> fixture.checkout().create(fixture.merchant(), key, request));
        assertThat(failure.code()).isEqualTo(ErrorCode.BILLING_PAYMENT_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select status from billing_invoices where merchant_id=?",
            String.class, fixture.merchant())).isEqualTo("PENDING_PAYMENT");
        oneReservation(fixture);
    }

    @Test void historicalPlanFingerprintRequiresSafeStatusReadWithoutAnotherProviderCall() throws Exception {
        var fixture = fixture(false);
        var request = request(fixture);
        String key = "historical-" + UUID.randomUUID();
        var original = fixture.checkout().create(fixture.merchant(), key, request);
        String historical = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(request.planCode().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("update billing_invoices set request_fingerprint=? where id=?", historical, original.invoiceId());
        jdbc.update("delete from billing_quotes where id=?", request.quoteId());
        var failure = assertThrows(ApiException.class, () -> fixture.checkout().create(fixture.merchant(), key, request));
        assertThat(failure.status().value()).isEqualTo(409);
        assertThat(failure.code()).isEqualTo(ErrorCode.BILLING_PAYMENT_UNAVAILABLE);
        assertThat(failure.details()).containsEntry("invoiceId", original.invoiceId())
            .containsEntry("statusPath", "/api/v1/merchants/" + fixture.merchant() + "/billing/invoices/" + original.invoiceId());
        oneReservation(fixture);
    }
}
