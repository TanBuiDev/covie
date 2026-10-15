package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.RequestIdAccessor;

class AdminBillingGrantServiceTest {
    @Test void operatorApprovalIsRequiredBeforeAwardingFreeAccess() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var service=new AdminBillingGrantService(jdbc,mock(AdminAccessPolicy.class),
            ()->Instant.parse("2026-10-16T00:00:00Z"),mock(AuditLogWriter.class),
            mock(RequestIdAccessor.class),mock(OutboxEventWriter.class),false);
        assertThrows(RuntimeException.class,()->service.grant(UUID.randomUUID(),
            new AdminBillingGrantService.Request(Instant.parse("2026-11-16T00:00:00Z"),"Test")));
        verifyNoInteractions(jdbc);
    }
}
