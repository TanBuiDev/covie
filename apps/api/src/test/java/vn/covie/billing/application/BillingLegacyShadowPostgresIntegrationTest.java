package vn.covie.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.support.PostgresIntegrationTest;

@SpringBootTest(properties = {"covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
@Transactional
class BillingLegacyShadowPostgresIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void realShadowSqlIncludesExactStartExcludesExactEndFutureRevokedAndCountsOverlappingGrantsOnce() {
        Instant now = Instant.parse("2026-10-18T00:00:00Z");
        Instant cutoff = now.minusSeconds(86400);
        var service = new BillingLegacyMigrationService(jdbc, mock(AdminAccessPolicy.class),
            mock(SystemSettingResolver.class), () -> now, mock(AuditLogWriter.class), mock(RequestIdAccessor.class), false);
        var before = service.preview(cutoff).shadow();
        for (String state : new String[]{"EXACT_START", "EXACT_END", "FUTURE", "REVOKED", "EXPIRED", "OVERLAP", "NONE"}) {
            UUID merchant = UUID.randomUUID();
            jdbc.update("""
                insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
                values (?,'Shadow boundary',?,'ACTIVE','APPROVED',?,?,0)
                """, merchant, "shadow-boundary-" + merchant, Timestamp.from(now.minusSeconds(172800)), Timestamp.from(now));
            if (state.equals("NONE")) continue;
            Instant start = state.equals("EXACT_START") ? now
                : state.equals("FUTURE") ? now.plusMillis(1) : now.minusSeconds(2);
            Instant end = state.equals("EXACT_END") ? now
                : state.equals("EXPIRED") ? now.minusMillis(1) : now.plusSeconds(2);
            int count = state.equals("OVERLAP") ? 2 : 1;
            for (int index = 0; index < count; index++) {
                jdbc.update("""
                    insert into billing_entitlement_grants(id,merchant_id,source_type,source_id,valid_from,valid_until,
                        revoked_at,reason,created_at) values (?,?,'ADMIN',?,?,?,?,'Fixed-clock SQL boundary fixture',?)
                    """, UUID.randomUUID(), merchant, UUID.randomUUID(), Timestamp.from(start), Timestamp.from(end),
                    state.equals("REVOKED") ? Timestamp.from(now) : null, Timestamp.from(now));
            }
        }
        var after = service.preview(cutoff).shadow();
        assertThat(after.evaluatedMerchants()).isEqualTo(before.evaluatedMerchants() + 7);
        assertThat(after.currentlyAllowed()).isEqualTo(before.currentlyAllowed() + 7);
        assertThat(after.wouldAllow()).isEqualTo(before.wouldAllow() + 2);
        assertThat(after.wouldDeny()).isEqualTo(before.wouldDeny() + 5);
        assertThat(after.enforcementEnabled()).isFalse();
        assertThat(after.observedAt()).isEqualTo(now);
    }
}
