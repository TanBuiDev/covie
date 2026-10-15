package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.time.AppClock;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.settings.application.SystemSettingContext;

/**
 * Called within a merchant creation transaction.
 * One trial per owner; any additional shops require a subscription payment.
 */
@Service
public class BillingTrialService {
    private final JdbcTemplate jdbc;
    private final BillingCatalogService catalog;
    private final AppClock clock;
    private final SystemSettingResolver settings;

    public BillingTrialService(JdbcTemplate jdbc, BillingCatalogService catalog, AppClock clock,
            SystemSettingResolver settings) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.clock = clock;
        this.settings = settings;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean grantEligibleTrial(UUID merchantId, UUID ownerUserId) {
        // Observe-only mode does not alter legacy merchant entitlement or depend on billing rows.
        if (!settings.bool("billing.enforcement_enabled", SystemSettingContext.globalOnly())) return false;
        BillingCatalogService.Policy policy = catalog.currentPolicy();
        if (!trialEnabled(policy)) return false;
        String planCode = jdbc.query("""
            select code from billing_plans where retired_at is null order by code limit 1
            """, rs -> rs.next() ? rs.getString(1) : null);
        if (planCode == null) return false;
        Instant now = clock.now();
        Instant end = now.plus(Duration.ofDays(policy.trialDays()));
        int claimed = jdbc.update("""
            insert into billing_trial_claims(owner_user_id,merchant_id,policy_version_id,claimed_at)
            values (?,?,?,?) on conflict (owner_user_id) do nothing
            """, ownerUserId, merchantId, policy.id(), Timestamp.from(now));
        if (claimed == 0) return false;
        jdbc.update("""
            insert into billing_entitlement_grants
              (id,merchant_id,source_type,source_id,valid_from,valid_until,reason,created_at)
            values (?,?,'TRIAL',?,?,?,'Admin-configured seller trial',?)
            """, UUID.randomUUID(), merchantId, merchantId, Timestamp.from(now),
            Timestamp.from(end), Timestamp.from(now));
        jdbc.update("""
            insert into merchant_subscriptions
              (merchant_id,plan_code,status,period_start,period_end,trial_end,grace_until,created_at,updated_at)
            values (?,?,'TRIALING',?,?,?,?,?,?)
            """, merchantId, planCode, Timestamp.from(now), Timestamp.from(end),
            Timestamp.from(end), Timestamp.from(end.plus(Duration.ofDays(policy.graceDays()))),
            Timestamp.from(now), Timestamp.from(now));
        return true;
    }

    public static boolean trialEnabled(BillingCatalogService.Policy policy) {
        return policy != null && "TRIAL".equals(policy.onboardingMode())
            && policy.trialDays() > 0 && policy.trialDays() <= 365;
    }
}
