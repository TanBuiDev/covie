package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.web.RequestIdAccessor;

class BillingFinanceCaseServiceTest {
    @Test void rejectsInvalidRefundRequestBeforeAnyLedgerMutation() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var service=new BillingFinanceCaseService(jdbc,mock(BillingCheckoutService.class),
            mock(AdminAccessPolicy.class),()->java.time.Instant.EPOCH,
            mock(AuditLogWriter.class),mock(RequestIdAccessor.class),mock(OutboxEventWriter.class),false);
        assertThrows(RuntimeException.class,()->service.create(UUID.randomUUID(),UUID.randomUUID(),
            new BillingFinanceCaseService.Request("REFUND"," ")));
        verifyNoInteractions(jdbc);
    }

    @Test void manualRefundConfirmationIsNotEnabledWithoutOperatorApproval() {
        var service=new BillingFinanceCaseService(mock(JdbcTemplate.class),
            mock(BillingCheckoutService.class),mock(AdminAccessPolicy.class),
            ()->java.time.Instant.EPOCH,mock(AuditLogWriter.class),mock(RequestIdAccessor.class),
            mock(OutboxEventWriter.class),false);
        assertThrows(RuntimeException.class,()->service.resolve(UUID.randomUUID(),
            new BillingFinanceCaseService.Resolution("CONFIRMED","done","BANK_TX_1234")));
    }
    @Test void nullFinanceCaseTransitionIsRejectedWithoutMutation(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var service=new BillingFinanceCaseService(jdbc,mock(BillingCheckoutService.class),
            mock(AdminAccessPolicy.class),()->java.time.Instant.EPOCH,
            mock(AuditLogWriter.class),mock(RequestIdAccessor.class),mock(OutboxEventWriter.class),false);
        assertThrows(RuntimeException.class,()->service.resolve(UUID.randomUUID(),
            new BillingFinanceCaseService.Resolution(null,"reviewed",null)));
        verifyNoInteractions(jdbc);
    }

}
