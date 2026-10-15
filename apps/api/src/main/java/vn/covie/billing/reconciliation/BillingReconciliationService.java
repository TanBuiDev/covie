package vn.covie.billing.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.shared.time.AppClock;

/**
 * Queries payOS authoritative link status, stores a privacy-minimal observation.
 * This does not settle invoices or create entitlements: PAID without an authenticated
 * webhook is an explicit operational incident awaiting verified settlement flow.
 */
@Service
public class BillingReconciliationService {
    private final JdbcTemplate jdbc;
    private final PayosGateway gateway;
    private final AppClock clock;

    public BillingReconciliationService(JdbcTemplate jdbc, PayosGateway gateway, AppClock clock) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Candidate> pending(int maximum) {
        if (maximum < 1 || maximum > 100) throw new IllegalArgumentException("Invalid reconciliation batch");
        return jdbc.query("""
                select i.id invoice_id, i.payos_order_code order_code,
                       i.amount_vnd amount, a.provider_payment_link_id link_id
                from billing_invoices i
                join billing_payment_attempts a on a.invoice_id = i.id
                left join billing_reconciliation_observations obs on obs.invoice_id=i.id
                where i.status = 'PENDING_PAYMENT' and a.provider='PAYOS'
                  and i.payos_order_code is not null
                order by obs.last_checked_at asc nulls first, i.created_at asc
                limit ?
                """,
            (rs,n) -> new Candidate((UUID) rs.getObject("invoice_id"),
                rs.getLong("order_code"), rs.getLong("amount"),
                rs.getString("link_id")), maximum);
    }

    public Observation queryProvider(Candidate candidate) {
        if (!gateway.isEnabled()) return new Observation(candidate.invoiceId(), "UNAVAILABLE", "PROVIDER_FAILURE");
        final JsonNode response;
        try {
            response = gateway.lookup(candidate.orderCode());
        } catch (PayosGateway.ProviderUnavailableException ex) {
            return new Observation(candidate.invoiceId(), "UNAVAILABLE", "PROVIDER_FAILURE");
        }
        // payOS includes an HMAC of response.data. A GET that is merely HTTP 200
        // is not sufficient to authorize invoice settlement or reconcile to PAID.
        if (!gateway.verifyResponseSignature(response)) {
            return new Observation(candidate.invoiceId(), "UNVERIFIED", "PROVIDER_FAILURE");
        }
        return compare(candidate, response);
    }

    /** No exception, raw provider body, bank account or API key leaks into observation storage. */
    public static Observation compare(Candidate candidate, JsonNode response) {
        if (response == null || !"00".equals(response.path("code").asText(""))) {
            return new Observation(candidate.invoiceId(), "UNAVAILABLE", "PROVIDER_FAILURE");
        }
        JsonNode data = response.path("data");
        String status = data.path("status").asText("UNKNOWN");
        if (!status.matches("[A-Z_]{1,40}")) status = "UNKNOWN";
        if (data.path("orderCode").asLong(-1) != candidate.orderCode()) {
            return new Observation(candidate.invoiceId(), status, "ORDER_MISMATCH");
        }
        if (data.path("amount").asLong(-1) != candidate.amount()) {
            return new Observation(candidate.invoiceId(), status, "AMOUNT_MISMATCH");
        }
        if (candidate.linkId() == null || !candidate.linkId().equals(data.path("id").asText(""))) {
            return new Observation(candidate.invoiceId(), status, "LINK_MISMATCH");
        }
        if ("PAID".equals(status)) {
            long paid = data.path("amountPaid").asLong(-1);
            return new Observation(candidate.invoiceId(), status,
                paid == candidate.amount() ? "PAID_PENDING_WEBHOOK" : "AMOUNT_MISMATCH");
        }
        if ("PENDING".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status)) {
            return new Observation(candidate.invoiceId(), status, "NONE");
        }
        return new Observation(candidate.invoiceId(), status, "UNEXPECTED_STATUS");
    }

    @Transactional
    public void record(Observation observation) {
        // Serialize observation with an invoice still in PENDING_PAYMENT.
        Integer outstanding = jdbc.queryForObject("""
            select count(*) from billing_invoices where id=? and status='PENDING_PAYMENT'
            """, Integer.class, observation.invoiceId());
        if (outstanding == null || outstanding == 0) return;
        Instant now = clock.now();
        jdbc.update("""
            insert into billing_reconciliation_observations
              (invoice_id,provider,provider_status,discrepancy,last_checked_at)
            values (?,'PAYOS',?,?,?)
            on conflict(invoice_id) do update set
              provider_status=excluded.provider_status,discrepancy=excluded.discrepancy,
              last_checked_at=excluded.last_checked_at,
              revision=billing_reconciliation_observations.revision+1
            """, observation.invoiceId(), observation.providerStatus(), observation.discrepancy(),
                Timestamp.from(now));
    }

    public record Candidate(UUID invoiceId, long orderCode, long amount, String linkId) {}
    public record Observation(UUID invoiceId, String providerStatus, String discrepancy) {}
}
