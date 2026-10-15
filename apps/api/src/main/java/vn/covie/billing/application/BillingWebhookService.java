package vn.covie.billing.application;

import com.fasterxml.jackson.databind.JsonNode;
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
import vn.covie.billing.payos.PayosGateway;
import vn.covie.billing.payos.PayosSignature;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/**
 * Signed payOS webhook is the only payment-success write path.
 * This service never accepts browser redirects as proof of payment.
 */
@Service
public class BillingWebhookService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PayosGateway payos;
    private final PayosSignature signatures;
    private final AppClock clock;
    private final OutboxEventWriter outbox;

    public BillingWebhookService(JdbcTemplate jdbc, ObjectMapper json, PayosGateway payos,
            PayosSignature signatures, AppClock clock, OutboxEventWriter outbox) {
        this.jdbc = jdbc; this.json = json; this.payos = payos;
        this.signatures = signatures; this.clock = clock; this.outbox = outbox;
    }

    @Transactional
    public String receive(String raw) {
        if (!payos.canVerifyWebhook()) {
            throw error(ErrorCode.BILLING_PAYMENT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "payOS is disabled.");
        }
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            throw error(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Webhook payload invalid.");
        }
        JsonNode root;
        try { root = json.readTree(raw); }
        catch (Exception ex) {
            throw error(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Invalid webhook JSON.");
        }
        JsonNode data = root.path("data");
        if (!signatures.verifyWebhook(data, root.path("signature").asText(""), payos.checksumKey())) {
            throw error(ErrorCode.ACCESS_DENIED, HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
        }
        if (!root.path("success").asBoolean(false) || !"00".equals(root.path("code").asText())) {
            return "ignored";
        }
        long orderCode = data.path("orderCode").asLong(-1);
        long amount = data.path("amount").asLong(-1);
        String currency = data.path("currency").asText("");
        String linkId = data.path("paymentLinkId").asText("");
        String reference = data.path("reference").asText("");
        if (orderCode < 1 || amount < 1 || !"VND".equals(currency) || linkId.isBlank()
                || reference.isBlank() || !"00".equals(data.path("code").asText(""))) {
            throw error(ErrorCode.BILLING_PRICE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Payment event incomplete.");
        }
        var matches = jdbc.query("""
            select i.id invoice_id,i.merchant_id,i.status invoice_status,i.amount_vnd,
                   i.period_start,i.period_end,i.grace_until,i.plan_version_id,
                   a.id attempt_id,a.provider_payment_link_id,a.status attempt_status
            from billing_invoices i join billing_payment_attempts a on a.invoice_id=i.id
            where i.payos_order_code=? and a.provider='PAYOS'
            for update of i,a
            """, (rs, row) -> new Payment(
                (UUID) rs.getObject("invoice_id"), (UUID) rs.getObject("merchant_id"),
                rs.getString("invoice_status"), rs.getLong("amount_vnd"),
                rs.getTimestamp("period_start").toInstant(), rs.getTimestamp("period_end").toInstant(),
                rs.getTimestamp("grace_until").toInstant(),
                (UUID) rs.getObject("plan_version_id"), (UUID) rs.getObject("attempt_id"),
                rs.getString("provider_payment_link_id"), rs.getString("attempt_status")), orderCode);
        if (matches.isEmpty()) {
            // payOS confirm-webhook probes with the documented sample order 123.
            // Samples must NEVER create an invoice, grant or subscription.
            if (orderCode == 123 && "VQRIO123".equals(data.path("description").asText())) return "sample";
            throw error(ErrorCode.BILLING_PAYMENT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Payment not yet registered.");
        }
        Payment payment = matches.getFirst();
        if (payment.amount() != amount || !linkId.equals(payment.linkId())) {
            throw error(ErrorCode.BILLING_PRICE_INVALID, HttpStatus.CONFLICT, "Payment reference or amount mismatch.");
        }
        String eventKey = reference.isBlank() ? "sig:" + root.path("signature").asText() :
            "ref:" + reference;
        if (eventKey.length() > 180) {
            throw error(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Reference too long.");
        }
        String digest = sha256(raw);
        UUID eventId = UUID.randomUUID();
        int added = jdbc.update("""
            insert into billing_webhook_inbox
             (id,provider,provider_event_id,payload_sha256,verified,processing_status,received_at)
            values (?,'PAYOS',?, ?,true,'PENDING',?)
            on conflict (provider,provider_event_id) do nothing
            """, eventId, eventKey, digest, Timestamp.from(clock.now()));
        if (added == 0) return "duplicate";
        if ("PAID".equals(payment.invoiceStatus())) {
            // A second signed event for a paid invoice must not leave a forever-pending inbox row.
            jdbc.update("update billing_webhook_inbox set processing_status='DONE',processed_at=? where id=?",
                Timestamp.from(clock.now()), eventId);
            return "duplicate";
        }
        if (!"PENDING_PAYMENT".equals(payment.invoiceStatus())) {
            throw error(ErrorCode.BILLING_PRICE_INVALID, HttpStatus.CONFLICT, "Invoice is not payable.");
        }

        String planCode = jdbc.queryForObject("""
            select plan_code from billing_plan_versions where id=?
            """, String.class, payment.planVersionId());
        int attempted = jdbc.update("""
            update billing_payment_attempts
            set status='SUCCEEDED', received_amount_vnd=?, provider_transaction_id=?, settled_at=?
            where id=? and status in ('CREATED','PENDING')
            """, amount, reference, Timestamp.from(clock.now()), payment.attemptId());
        if (attempted != 1) throw new IllegalStateException("Payment attempt settlement race.");
        int settled = jdbc.update("""
            update billing_invoices set status='PAID', paid_at=?, version=version+1
            where id=? and status='PENDING_PAYMENT'
            """, Timestamp.from(clock.now()), payment.invoiceId());
        if (settled != 1) throw new IllegalStateException("Invoice settlement race.");
        jdbc.update("""
            insert into billing_entitlement_grants
              (id, merchant_id, source_type, source_id, valid_from, valid_until, reason, created_at)
            values (?,?,'PAID_INVOICE',?,?,?,'Verified payOS settlement',?)
            on conflict (source_type, source_id) do nothing
            """, UUID.randomUUID(), payment.merchantId(), payment.invoiceId(),
            Timestamp.from(payment.periodStart()), Timestamp.from(payment.periodEnd()),
            Timestamp.from(clock.now()));
        jdbc.update("""
            insert into merchant_subscriptions
             (merchant_id,plan_code,status,period_start,period_end,grace_until,created_at,updated_at)
            values (?,?,'ACTIVE',?,?,?,?,?)
            on conflict(merchant_id) do update set
              status='ACTIVE', plan_code=excluded.plan_code,
              period_end=greatest(merchant_subscriptions.period_end,excluded.period_end),
              grace_until=greatest(merchant_subscriptions.grace_until,excluded.grace_until),
              period_start=coalesce(merchant_subscriptions.period_start,excluded.period_start),
              updated_at=excluded.updated_at,version=merchant_subscriptions.version+1
            """, payment.merchantId(), planCode, Timestamp.from(payment.periodStart()),
            Timestamp.from(payment.periodEnd()), Timestamp.from(payment.graceUntil()),
            Timestamp.from(clock.now()), Timestamp.from(clock.now()));
        jdbc.update("""
            update billing_webhook_inbox set processing_status='DONE',processed_at=?
            where id=?
            """, Timestamp.from(clock.now()), eventId);
        outbox.append(new OutboxEventIntent("SELLER_SUBSCRIPTION_PAYMENT_SETTLED", "BILLING_INVOICE",
            payment.invoiceId(), Map.of("merchantId", payment.merchantId().toString(),
                                       "invoiceId", payment.invoiceId().toString())));
        return "settled";
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    private static ApiException error(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message);
    }
    private record Payment(UUID invoiceId, UUID merchantId, String invoiceStatus, long amount,
                           Instant periodStart, Instant periodEnd, Instant graceUntil, UUID planVersionId,
                           UUID attemptId, String linkId, String attemptStatus) {}
}
