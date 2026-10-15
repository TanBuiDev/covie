package vn.covie.billing.application;

import java.time.Instant;
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

    public BillingEntitlementService(JdbcTemplate jdbc, AppClock clock, SystemSettingResolver settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Eligibility evaluate(UUID merchantId) {
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
        boolean canSell = expiry != null;
        return new Eligibility(canSell, true, canSell ? "ACTIVE_ENTITLEMENT" : "SUBSCRIPTION_REQUIRED", expiry);
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
