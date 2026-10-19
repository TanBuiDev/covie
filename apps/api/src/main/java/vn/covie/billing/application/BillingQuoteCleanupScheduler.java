package vn.covie.billing.application;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.time.AppClock;

/** Delete only expired quote tokens, never invoices or payment attempts. */
@Service
public class BillingQuoteCleanupScheduler {
    private final JdbcTemplate jdbc;
    private final AppClock clock;
    public BillingQuoteCleanupScheduler(JdbcTemplate jdbc, AppClock clock) {
        this.jdbc=jdbc;this.clock=clock;
    }
    @Scheduled(cron="${COVIE_BILLING_QUOTE_CLEANUP_CRON:0 30 2 * * *}",zone="Asia/Ho_Chi_Minh")
    @Transactional
    public void prune() {
        Timestamp cutoff=Timestamp.from(clock.now().minusSeconds(86400));
        for (int batch=0;batch<20;batch++) {
            int removed=jdbc.update("""
                delete from billing_quotes where id in (
                    select id from billing_quotes where expires_at < ?
                    order by expires_at limit 1000
                )
                """,cutoff);
            if (removed<1000) break;
        }
    }
}
