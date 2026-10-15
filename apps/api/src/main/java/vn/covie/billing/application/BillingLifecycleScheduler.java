package vn.covie.billing.application;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.time.AppClock;

/**
 * Subscription statuses are informational: real canSell checks the timed entitlement grant.
 * Expiration never deletes catalog, orders, social interactions or memberships.
 */
@Service
public class BillingLifecycleScheduler {
    private final JdbcTemplate jdbc;
    private final AppClock clock;

    public BillingLifecycleScheduler(JdbcTemplate jdbc, AppClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${COVIE_BILLING_LIFECYCLE_CRON:0 5 * * * *}")
    @Transactional
    public void refreshStatuses() {
        var now = Timestamp.from(clock.now());
        jdbc.update("""
            update merchant_subscriptions set
                status = case
                    when cancel_at_period_end then 'CANCELLED'
                    when coalesce(grace_until,period_end) > ? then 'PAST_DUE'
                    else 'EXPIRED'
                end,
                updated_at = ?, version = version + 1
            where period_end <= ? and status in ('ACTIVE','TRIALING','PAST_DUE')
              and status <> case
                  when cancel_at_period_end then 'CANCELLED'
                  when coalesce(grace_until,period_end) > ? then 'PAST_DUE'
                  else 'EXPIRED' end
            """, now, now, now, now);
    }
}
