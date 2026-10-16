package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;

/** Real PostgreSQL regression for merchant 501+ starvation and configurable offsets. */
class BillingReminderBatchIntegrationTest {
    private static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("billing_reminder_batch_test")
            .withUsername("billing").withPassword("billing-test-only");
    private static JdbcTemplate jdbc;

    @BeforeAll static void setup() {
        PG.start();
        var ds=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
    }
    @AfterAll static void shutdown() { PG.stop(); }

    @Test void deliversBeyondFirstFiveHundredAndDoesNotRepeatDispatch() {
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        Instant now=Instant.now();
        LocalDate expiryDay=now.atZone(zone).toLocalDate().plusDays(2);
        Instant until=expiryDay.atTime(9,0).atZone(zone).toInstant();
        for(int i=0;i<501;i++) {
            UUID merchantId=UUID.randomUUID();
            String slug="billing-reminder-"+merchantId;
            jdbc.update("""
                insert into merchant_profiles
                  (id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                values (?,'Reminder merchant',?,'ACTIVE','APPROVED',?,?,0)
                """,merchantId,slug,Timestamp.from(now),Timestamp.from(now));
            jdbc.update("""
                insert into merchant_subscriptions
                  (merchant_id,plan_code,status,period_start,period_end,created_at,updated_at)
                values (?,'SELLER_STANDARD','ACTIVE',?,?,?,?)
                """,merchantId,Timestamp.from(now.minusSeconds(86400)),
                Timestamp.from(until),Timestamp.from(now),Timestamp.from(now));
        }
        var settings=mock(SystemSettingResolver.class);
        when(settings.bool(eq("billing.reminders_enabled"),any(SystemSettingContext.class)))
            .thenReturn(true);
        when(settings.integer(eq("billing.reminder_first_days"),any(SystemSettingContext.class)))
            .thenReturn(2);
        when(settings.integer(eq("billing.reminder_second_days"),any(SystemSettingContext.class)))
            .thenReturn(3);
        when(settings.integer(eq("billing.reminder_last_days"),any(SystemSettingContext.class)))
            .thenReturn(1);
        OutboxEventWriter events=mock(OutboxEventWriter.class);
        var scheduler=new BillingReminderScheduler(jdbc,()->now,settings,events);
        scheduler.remind();
        assertEquals(501L,jdbc.queryForObject("""
            select count(*) from billing_reminder_dispatches where days_before=2
            """,Long.class));
        verify(events,times(501)).append(any(OutboxEventIntent.class));
        scheduler.remind();
        assertEquals(501L,jdbc.queryForObject("""
            select count(*) from billing_reminder_dispatches
            """,Long.class));
        verify(events,times(501)).append(any(OutboxEventIntent.class));
    }
}
