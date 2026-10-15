package vn.covie.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/**
 * Creating a seller payment link never invokes the buyer checkout.
 * This first iteration reserves a unique provider order code and persists
 * an invoice with immutable plan/policy snapshots.
 */
@Service
public class BillingCheckoutService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AppClock clock;
    private final BillingCatalogService catalog;
    private final PayosGateway payos;
    private final MerchantMembershipRepository memberships;
    private final CurrentActorProvider actors;
    private final TransactionTemplate transactions;

    public BillingCheckoutService(JdbcTemplate jdbc, ObjectMapper json, AppClock clock,
            BillingCatalogService catalog, PayosGateway payos,
            MerchantMembershipRepository memberships, CurrentActorProvider actors,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.json = json; this.clock = clock; this.catalog = catalog;
        this.payos = payos; this.memberships = memberships; this.actors = actors;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public CheckoutResult create(UUID merchantId, String idempotencyKey, CheckoutRequest request) {
        requireOwner(merchantId);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._-]{8,80}")) {
            throw error(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, HttpStatus.BAD_REQUEST, "Idempotency-Key is required.");
        }
        if (request == null || request.planCode() == null || !request.planCode().matches("[A-Z0-9_]{1,60}")) {
            throw error(ErrorCode.BILLING_PRICE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid plan.");
        }
        if (!payos.isEnabled()) {
            throw error(ErrorCode.BILLING_PAYMENT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "payOS is not enabled.");
        }
        // Commit the invoice/orderCode reservation BEFORE calling payOS. A PSP response
        // must never be followed by an uncommitted local invoice.
        Reservation reservation = transactions.execute(tx -> reserve(merchantId, idempotencyKey, request));
        if (reservation == null) throw new IllegalStateException("Billing reservation failed.");
        if (reservation.existing() != null) return reservation.existing();
        // No DB transaction holds merchant locks during a potentially slow network call.
        var link = payos.createPaymentLink(merchantId, reservation.orderCode(),
            reservation.amountVnd(), "COVIE " + reservation.orderCode(),
            reservation.expiresAt().getEpochSecond());
        transactions.executeWithoutResult(tx -> {
            int updated = jdbc.update("""
                update billing_payment_attempts
                set status='PENDING', checkout_url=?, qr_payload=?, provider_payment_link_id=?
                where id=? and status='CREATED'
                """, link.checkoutUrl(), link.qrPayload(), link.paymentLinkId(), reservation.attemptId());
            if (updated != 1) {
                // A signed payment callback can settle while the payOS POST is in flight.
                Boolean settled = jdbc.queryForObject("""
                    select exists (
                       select 1 from billing_invoices i
                       join billing_payment_attempts a on a.invoice_id=i.id
                       where i.id=? and i.status='PAID' and a.status='SUCCEEDED'
                         and a.provider_payment_link_id=?
                    )
                    """, Boolean.class, reservation.invoiceId(), link.paymentLinkId());
                if (!Boolean.TRUE.equals(settled)) {
                    throw new IllegalStateException("Payment link persistence race.");
                }
            }
        });
        String invoiceStatus = jdbc.queryForObject(
            "select status from billing_invoices where id=?", String.class, reservation.invoiceId());
        return new CheckoutResult(reservation.invoiceId(), reservation.orderCode(),
            reservation.amountVnd(), link.checkoutUrl(), link.qrPayload(),
            reservation.expiresAt(), invoiceStatus);
    }

    private Reservation reserve(UUID merchantId, String idempotencyKey, CheckoutRequest request) {
        // One outstanding create/renewal per merchant at a time, including competing requests.
        var merchantLock = jdbc.query("select id from merchant_profiles where id=? and deleted_at is null for update",
                (rs, row) -> rs.getObject(1), merchantId);
        if (merchantLock.isEmpty()) {
            throw error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant not found.");
        }
        String fingerprint = sha256(request.planCode());
        var old = jdbc.query("""
                select i.id, i.request_fingerprint, i.status, a.checkout_url, a.qr_payload, i.expires_at,
                       i.payos_order_code, i.amount_vnd
                from billing_invoices i left join billing_payment_attempts a on a.invoice_id=i.id
                where i.merchant_id=? and i.idempotency_key=?
                """, (rs, row) -> new PreviousPayment(
                    (UUID) rs.getObject("id"), rs.getString("request_fingerprint"),
                    rs.getString("status"), rs.getString("checkout_url"), rs.getString("qr_payload"),
                    rs.getTimestamp("expires_at").toInstant(), rs.getLong("payos_order_code"),
                    rs.getLong("amount_vnd")), merchantId, idempotencyKey);
        if (!old.isEmpty()) {
            PreviousPayment prior = old.getFirst();
            if (!prior.fingerprint().equals(fingerprint)) {
                throw error(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST, HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with another plan.");
            }
            if (prior.checkoutUrl() == null) {
                throw error(ErrorCode.BILLING_PAYMENT_UNAVAILABLE, HttpStatus.CONFLICT, "Payment initialization pending.");
            }
            return Reservation.previous(new CheckoutResult(
                prior.invoiceId(), prior.orderCode(), prior.amount(),
                prior.checkoutUrl(), prior.qrPayload(), prior.expiresAt(), prior.status()));
        }
        Boolean pending = jdbc.queryForObject("""
            select exists (
              select 1 from billing_invoices
              where merchant_id=? and status='PENDING_PAYMENT' and expires_at > ?
            )
            """, Boolean.class, merchantId, Timestamp.from(clock.now()));
        if (Boolean.TRUE.equals(pending)) {
            throw error(ErrorCode.BILLING_PAYMENT_UNAVAILABLE, HttpStatus.CONFLICT,
                "Another payment link is still pending. Wait or check the existing invoice.");
        }
        var offer = catalog.availablePlans().stream()
            .filter(p -> p.code().equals(request.planCode()))
            .findFirst().orElseThrow(() -> error(ErrorCode.BILLING_PRICE_INVALID,
                HttpStatus.UNPROCESSABLE_ENTITY, "Plan is not currently available."));
        if (offer.priceVnd() <= 0) {
            throw error(ErrorCode.BILLING_PRICE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                "Zero-value plans require an Admin entitlement grant, not payOS.");
        }
        var policy = catalog.currentPolicy();
        Instant now = clock.now();
        Instant expires = now.plusSeconds(15 * 60);
        Timestamp at = Timestamp.from(now);
        Instant paidThrough = jdbc.query("""
            select max(valid_until) from billing_entitlement_grants
            where merchant_id=? and revoked_at is null and valid_until > ?
            """, rs -> {
                rs.next();
                Timestamp end = rs.getTimestamp(1);
                return end == null ? null : end.toInstant();
            }, merchantId, at);
        Instant periodStart = paidThrough != null && paidThrough.isAfter(now) ? paidThrough : now;
        Instant periodEnd = BillingPeriodCalculator.addMonths(periodStart, offer.cycleMonths());
        UUID invoiceId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Long orderCode = jdbc.queryForObject("select nextval('billing_payos_order_code_seq')", Long.class);
        if (orderCode == null) throw new IllegalStateException("Missing payOS order code.");
        String number = "CB-" + orderCode;
        String snapshot;
        try {
            snapshot = json.writeValueAsString(Map.of("planCode", offer.code(),
                "priceRevision", offer.revision(), "priceVnd", offer.priceVnd(),
                "cycleMonths", offer.cycleMonths(), "policyRevision", policy.revision()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize billing snapshot", ex);
        }
        jdbc.update("""
            insert into billing_invoices
              (id,invoice_number,merchant_id,plan_version_id,policy_version_id,status,
               amount_vnd,period_start,period_end,pricing_snapshot,idempotency_key,
               request_fingerprint,created_at,expires_at,payos_order_code,grace_until)
            values (?,?,?,?,?,'PENDING_PAYMENT',?,?,?,?::jsonb,?,?,?,?,?,?)
            """, invoiceId, number, merchantId, offer.id(), policy.id(), offer.priceVnd(),
            Timestamp.from(periodStart), Timestamp.from(periodEnd), snapshot, idempotencyKey,
            fingerprint, at, Timestamp.from(expires), orderCode,
            Timestamp.from(periodEnd.plusSeconds(86400L * policy.graceDays())));
        jdbc.update("""
            insert into billing_payment_attempts
              (id,invoice_id,provider,provider_order_id,status,expected_amount_vnd,created_at)
            values (?,?,'PAYOS',?,'CREATED',?,?)
            """, attemptId, invoiceId, orderCode.toString(), offer.priceVnd(), at);

        return new Reservation(invoiceId, attemptId, orderCode, offer.priceVnd(), expires, null);
    }

    private record Reservation(UUID invoiceId, UUID attemptId, long orderCode, long amountVnd,
                               Instant expiresAt, CheckoutResult existing) {
        static Reservation previous(CheckoutResult value) {
            return new Reservation(null, null, 0L, 0L, null, value);
        }
    }

    /**
     * Opt out of future automatic renewal. The existing prepaid period and
     * historical order obligations are never cancelled by this operation.
     */
    @Transactional
    public void cancelRenewal(UUID merchantId) {
        requireOwner(merchantId);
        int updated = jdbc.update("""
            update merchant_subscriptions set cancel_at_period_end=true,
                updated_at=?, version=version+1
            where merchant_id=? and status in ('ACTIVE','TRIALING','PAST_DUE')
              and cancel_at_period_end=false
            """, Timestamp.from(clock.now()), merchantId);
        if (updated == 0) {
            Boolean alreadyCancelled = jdbc.queryForObject("""
                select exists(select 1 from merchant_subscriptions
                    where merchant_id=? and cancel_at_period_end=true)
                """, Boolean.class, merchantId);
            if (!Boolean.TRUE.equals(alreadyCancelled)) {
                throw error(ErrorCode.BILLING_SUBSCRIPTION_REQUIRED, HttpStatus.CONFLICT,
                    "No active subscription to cancel.");
            }
        }
    }

    @Transactional(readOnly = true)
    public PaymentStatus paymentStatus(UUID merchantId, UUID invoiceId) {
        requireOwner(merchantId);
        var rows = jdbc.query("""
            select i.id, i.status, i.amount_vnd, i.period_start, i.period_end,
                   a.status provider_status, a.checkout_url, i.expires_at
            from billing_invoices i join billing_payment_attempts a on a.invoice_id=i.id
            where i.merchant_id=? and i.id=?
            """, (rs,i) -> new PaymentStatus(
                (UUID) rs.getObject("id"), rs.getString("status"),
                rs.getString("provider_status"), rs.getLong("amount_vnd"),
                rs.getTimestamp("period_start").toInstant(),
                rs.getTimestamp("period_end").toInstant(),
                rs.getString("checkout_url"), rs.getTimestamp("expires_at").toInstant()
            ), merchantId, invoiceId);
        if (rows.isEmpty()) throw error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice not found.");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public java.util.List<InvoiceSummary> invoices(UUID merchantId) {
        requireOwner(merchantId);
        return jdbc.query("""
            select id, invoice_number, status, amount_vnd, period_start, period_end, created_at
            from billing_invoices where merchant_id=? order by created_at desc limit 100
            """, (rs, i) -> new InvoiceSummary((UUID) rs.getObject("id"), rs.getString("invoice_number"),
            rs.getString("status"), rs.getLong("amount_vnd"), rs.getTimestamp("period_start").toInstant(),
            rs.getTimestamp("period_end").toInstant(), rs.getTimestamp("created_at").toInstant()), merchantId);
    }

    public void requireOwner(UUID merchantId) {
        var member = memberships.findActive(merchantId, actors.currentActor().userId())
            .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant not found."));
        if (member.role() != MerchantRole.OWNER) {
            throw error(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN, "Only owner can pay seller fees.");
        }
    }

    public void requireMember(UUID merchantId) {
        memberships.findActive(merchantId, actors.currentActor().userId())
            .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant not found."));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private static ApiException error(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message);
    }
    private record PreviousPayment(UUID invoiceId, String fingerprint, String status,
                                   String checkoutUrl, String qrPayload, Instant expiresAt,
                                   long orderCode, long amount) {}
    public record CheckoutRequest(String planCode) {}
    public record CheckoutResult(UUID invoiceId, long orderCode, long amountVnd,
                                 String checkoutUrl, String qrPayload, Instant expiresAt, String invoiceStatus) {}
    public record PaymentStatus(UUID invoiceId, String invoiceStatus, String paymentStatus,
                                long amountVnd, Instant periodStart, Instant periodEnd,
                                String checkoutUrl, Instant expiresAt) {}
    public record InvoiceSummary(UUID invoiceId, String invoiceNumber, String status,
                                 long amountVnd, Instant periodStart, Instant periodEnd, Instant createdAt) {}
}
