package vn.covie.billing.application;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.outbox.OutboxEventWriter;

class BillingReminderSchedulerTest {
    @Test void featureFlagDisablesRemindersAndDoesNotAccessDatabase() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SystemSettingResolver settings=mock(SystemSettingResolver.class);
        when(settings.bool(eq("billing.reminders_enabled"),any(SystemSettingContext.class)))
            .thenReturn(false);
        var scheduler=new BillingReminderScheduler(jdbc,()->java.time.Instant.EPOCH,settings,
            mock(OutboxEventWriter.class));
        scheduler.remind();
        verifyNoInteractions(jdbc);
    }
}
