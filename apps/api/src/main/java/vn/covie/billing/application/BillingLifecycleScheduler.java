package vn.covie.billing.application;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
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
    private SystemSettingResolver settings;
    private OutboxEventWriter outbox;

    @Autowired
    public void setNotificationDependencies(SystemSettingResolver settings, OutboxEventWriter outbox) {
        this.settings = settings;
        this.outbox = outbox;
    }

    public BillingLifecycleScheduler(JdbcTemplate jdbc, AppClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${COVIE_BILLING_LIFECYCLE_CRON:0 5 * * * *}")
    @Transactional
    public void refreshStatuses() {
        var now = Timestamp.from(clock.now());
        var changes = jdbc.query("""
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
            returning merchant_id,status
            """, (rs,n)->new Change((UUID)rs.getObject("merchant_id"),rs.getString("status")),
            now,now,now,now);
        if (outbox != null && settings != null
                && settings.bool("billing.reminders_enabled",SystemSettingContext.globalOnly())) {
            for(Change change:changes) {
                if ("EXPIRED".equals(change.status())) {
                    outbox.append(new OutboxEventIntent("BILLING_SUBSCRIPTION_EXPIRED","MERCHANT",
                        change.merchantId(),Map.of("merchantId",change.merchantId().toString())));
                }
            }
        }
    }

    private record Change(UUID merchantId,String status) {}
}
