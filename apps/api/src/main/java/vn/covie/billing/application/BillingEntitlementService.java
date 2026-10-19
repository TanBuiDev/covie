package vn.covie.billing.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/**
 * Server-side merchant eligibility. Buyer accounts are never billed.
 * Default is OBSERVE/OFF: preserve legacy merchants until explicit staged rollout.
 */
@Service
public class BillingEntitlementService {
    private final JdbcTemplate jdbc;
    private final AppClock clock;
    private final SystemSettingResolver settings;
    private vn.covie.billing.observability.BillingOperationMetrics operationMetrics;

    @org.springframework.beans.factory.annotation.Autowired
    public void setOperationMetrics(vn.covie.billing.observability.BillingOperationMetrics metrics) { this.operationMetrics = metrics; }

    public BillingEntitlementService(JdbcTemplate jdbc, AppClock clock, SystemSettingResolver settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Eligibility evaluate(UUID merchantId) {
        long started = System.nanoTime();
        try {
        boolean enforce = settings.bool("billing.enforcement_enabled", SystemSettingContext.globalOnly());
        if (!enforce) return new Eligibility(true, false, "ENFORCEMENT_DISABLED", null);
        Instant now = clock.now();
        Instant expiry = jdbc.query("""
            select max(valid_until) from billing_entitlement_grants
            where merchant_id = ? and revoked_at is null
              and valid_from <= ? and valid_until > ?
            """, rs -> {
                rs.next();
                java.sql.Timestamp until = rs.getTimestamp(1);
                return until == null ? null : until.toInstant();
            }, merchantId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        if (expiry != null) return new Eligibility(true, true, "ACTIVE_ENTITLEMENT", expiry);
        // Existing paid/trial access can continue until the policy snapshot's grace
        // timestamp. Refunds, revoked grants and explicit cancellation never earn grace.
        Instant grace = jdbc.query("""
            select s.grace_until
            from merchant_subscriptions s
            where s.merchant_id=? and s.status in ('ACTIVE','TRIALING','PAST_DUE')
              and s.cancel_at_period_end=false
              and s.period_end<=? and s.grace_until>?
              and exists (
                select 1 from billing_entitlement_grants g
                where g.merchant_id=s.merchant_id and g.revoked_at is null
                  and g.source_type in ('TRIAL','PAID_INVOICE') and g.valid_until=s.period_end
              )
            """, rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null,
            merchantId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        if (grace != null) return new Eligibility(false, true, "GRACE_PERIOD_READ_ONLY", grace);
        return new Eligibility(false, true, "SUBSCRIPTION_REQUIRED", null);
        } finally {
            if (operationMetrics != null) operationMetrics.entitlement(started, false);
        }
    }

    @Transactional(readOnly = true)
    public Set<UUID> merchantsAllowedToSell(Set<UUID> merchantIds) {
        long started = System.nanoTime();
        try {
        if (merchantIds.isEmpty()) return Set.of();
        if (!settings.bool("billing.enforcement_enabled", SystemSettingContext.globalOnly())) {
            return Set.copyOf(merchantIds);
        }
        return Set.copyOf(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
            select distinct merchant_id from billing_entitlement_grants
            where merchant_id in (:merchantIds) and revoked_at is null
              and valid_from <= :now and valid_until > :now
            """, Map.of("merchantIds", merchantIds, "now", java.sql.Timestamp.from(clock.now())),
            (result, row) -> result.getObject("merchant_id", UUID.class)));
        } finally {
            if (operationMetrics != null) operationMetrics.entitlement(started, true);
        }
    }

    @Transactional(readOnly = true)
    public void requireCanSell(UUID merchantId) {
        if (!evaluate(merchantId).canSell()) {
            throw new ApiException(ErrorCode.BILLING_SUBSCRIPTION_REQUIRED,
                HttpStatus.CONFLICT, "Seller subscription is required for this operation.");
        }
    }

    public record Eligibility(boolean canSell, boolean enforced, String reasonCode, Instant expiry) {}
}
