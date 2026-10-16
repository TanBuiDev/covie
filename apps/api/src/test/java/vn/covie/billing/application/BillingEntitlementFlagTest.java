package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import java.sql.ResultSet;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

class BillingEntitlementFlagTest {
    @Test void historicalMerchantsAreNotBlockedByDefault() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SystemSettingResolver settings=mock(SystemSettingResolver.class);
        when(settings.bool(eq("billing.enforcement_enabled"),any(SystemSettingContext.class)))
            .thenReturn(false);
        var entitlement=new BillingEntitlementService(jdbc,()->java.time.Instant.EPOCH,settings);
        var result=entitlement.evaluate(UUID.randomUUID());
        assertTrue(result.canSell());
        assertFalse(result.enforced());
        assertEquals("ENFORCEMENT_DISABLED",result.reasonCode());
        verifyNoInteractions(jdbc);
    }
    @Test void graceWindowIsReadOnlyAndCannotCreateNewSales() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SystemSettingResolver settings=mock(SystemSettingResolver.class);
        when(settings.bool(eq("billing.enforcement_enabled"),any(SystemSettingContext.class)))
            .thenReturn(true);
        Instant now=Instant.parse("2026-10-16T12:00:00Z");
        doAnswer(inv -> {
            String sql=inv.getArgument(0,String.class);
            ResultSet rs=mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getTimestamp(1)).thenReturn(sql.contains("max(valid_until)")
                ? null : Timestamp.from(now.plusSeconds(3*86400)));
            @SuppressWarnings("unchecked")
            ResultSetExtractor<Instant> extractor=(ResultSetExtractor<Instant>)inv.getArgument(1);
            return extractor.extractData(rs);
        }).when(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<Instant>>any(),
            any(),any(),any());
        var entitlement=new BillingEntitlementService(jdbc,()->now,settings);
        var result=entitlement.evaluate(UUID.randomUUID());
        assertTrue(result.enforced());
        assertFalse(result.canSell());
        assertEquals("GRACE_PERIOD_READ_ONLY",result.reasonCode());
        assertEquals(now.plusSeconds(3*86400),result.expiry());
        assertThrows(vn.covie.shared.web.ApiException.class,
            ()->entitlement.requireCanSell(UUID.randomUUID()));
    }
}

