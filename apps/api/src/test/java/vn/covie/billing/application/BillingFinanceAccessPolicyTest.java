package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.RequestIdAccessor;

class BillingFinanceAccessPolicyTest {
    private final UUID adminId=UUID.randomUUID();
    private final CurrentActor actor=new CurrentActor(adminId,Set.of(SystemRole.ADMIN),UUID.randomUUID());

    @Test void disabledFineGrainedModePreservesExistingAdminAccessWithoutFinanceQueries(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AdminAccessPolicy admin=mock(AdminAccessPolicy.class);
        SystemSettingResolver settings=mock(SystemSettingResolver.class);
        when(admin.requireAdmin()).thenReturn(actor);
        when(settings.bool(eq("billing.finance_roles_enabled"),any(SystemSettingContext.class)))
            .thenReturn(false);
        var service=new BillingFinanceAccessPolicy(jdbc,admin,settings,
            ()->Instant.EPOCH,mock(AuditLogWriter.class),mock(RequestIdAccessor.class),false);
        assertEquals(actor,service.requireReader());
        assertEquals(actor,service.requireOperator());
        assertEquals(actor,service.requireApprover());
        verifyNoInteractions(jdbc);
    }

    @Test void assignmentRequiresExplicitOperatorApprovalAndSecondAdmin(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AdminAccessPolicy admin=mock(AdminAccessPolicy.class);
        when(admin.requireAdmin()).thenReturn(actor);
        var denied=new BillingFinanceAccessPolicy(jdbc,admin,mock(SystemSettingResolver.class),
            ()->Instant.EPOCH,mock(AuditLogWriter.class),mock(RequestIdAccessor.class),false);
        assertThrows(RuntimeException.class,()->denied.assign(UUID.randomUUID(),"READER"));
        var enabled=new BillingFinanceAccessPolicy(jdbc,admin,mock(SystemSettingResolver.class),
            ()->Instant.EPOCH,mock(AuditLogWriter.class),mock(RequestIdAccessor.class),true);
        assertThrows(RuntimeException.class,()->enabled.assign(adminId,"APPROVER"));
        verifyNoInteractions(jdbc);
    }
}
