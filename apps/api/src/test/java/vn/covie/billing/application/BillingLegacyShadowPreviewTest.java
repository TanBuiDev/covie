package vn.covie.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.web.RequestIdAccessor;

class BillingLegacyShadowPreviewTest {
    @Test void legacyPreviewComputesWouldDenyWithoutChangingTheEnforcementOffDecision() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class))).thenReturn(2, 0);
        var service = new BillingLegacyMigrationService(jdbc, mock(AdminAccessPolicy.class),
            mock(SystemSettingResolver.class), () -> Instant.parse("2026-10-18T00:00:00Z"),
            mock(AuditLogWriter.class), mock(RequestIdAccessor.class), false);
        var response = new ObjectMapper().findAndRegisterModules().valueToTree(
            service.preview(Instant.parse("2026-10-17T00:00:00Z")));
        assertThat(response.path("eligibleMerchants").asInt()).isEqualTo(2);
        assertThat(response.path("shadow").isObject()).as("legacy preview must compute actual shadow billing decisions").isTrue();
        assertThat(response.path("shadow").path("decisionScope").asText()).isEqualTo("BILLING_ENTITLEMENT_ONLY");
        assertThat(response.path("shadow").path("enforcementEnabled").asBoolean()).isFalse();
        assertThat(response.path("shadow").path("currentlyAllowed").asInt()).isEqualTo(2);
        assertThat(response.path("shadow").path("wouldDeny").asInt()).isEqualTo(2);
    }
}
