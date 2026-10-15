package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;

/**
 * Admin-enabled daily reminders at 7, 3 and 1 days before prepaid period expiry.
 * Database claim + outbox insertion are atomic. No duplicate reminders across replicas.
 */
@Service
public class BillingReminderScheduler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final AppClock clock;
    private final SystemSettingResolver settings;
    private final OutboxEventWriter outbox;

    public BillingReminderScheduler(JdbcTemplate jdbc, AppClock clock,
            SystemSettingResolver settings, OutboxEventWriter outbox) {
        this.jdbc=jdbc; this.clock=clock; this.settings=settings; this.outbox=outbox;
    }

    @Scheduled(cron="${COVIE_BILLING_REMINDER_CRON:0 15 8 * * *}", zone="Asia/Ho_Chi_Minh")
    @Transactional
    public void remind() {
        if (!settings.bool("billing.reminders_enabled",SystemSettingContext.globalOnly())) return;
        Instant now=clock.now();
        LocalDate today=now.atZone(BUSINESS_ZONE).toLocalDate();
        for(int days : new int[]{7,3,1}) {
            LocalDate target=today.plusDays(days);
            var due=jdbc.query("""
                select merchant_id,period_end from merchant_subscriptions
                where status in ('ACTIVE','TRIALING')
                  and cancel_at_period_end=false and period_end is not null
                  and (period_end at time zone 'Asia/Ho_Chi_Minh')::date=?
                order by merchant_id limit 500
                """, (rs,n)-> new Due((UUID)rs.getObject("merchant_id"),
                    rs.getTimestamp("period_end").toInstant()),java.sql.Date.valueOf(target));
            for(Due item: due) {
                int inserted=jdbc.update("""
                    insert into billing_reminder_dispatches (merchant_id,period_end,days_before,created_at)
                    values (?,?,?,?) on conflict do nothing
                    """,item.merchantId(),Timestamp.from(item.periodEnd()),days,Timestamp.from(now));
                if(inserted==1) {
                    outbox.append(new OutboxEventIntent("BILLING_RENEWAL_REMINDER",
                        "MERCHANT",item.merchantId(),Map.of(
                            "merchantId",item.merchantId().toString(),"daysBefore",days,
                            "periodEnd",item.periodEnd().toString())));
                }
            }
        }
    }

    private record Due(UUID merchantId,Instant periodEnd) {}
}
