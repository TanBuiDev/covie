package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
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
}
