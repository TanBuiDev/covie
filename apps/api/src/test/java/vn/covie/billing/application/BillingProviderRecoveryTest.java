package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.billing.payos.PayosSignature;
import vn.covie.shared.outbox.OutboxEventWriter;

class BillingProviderRecoveryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PayosGateway gateway = mock(PayosGateway.class);
    private BillingWebhookService service() {
        return new BillingWebhookService(mock(JdbcTemplate.class),json,gateway,
            new PayosSignature(json),()->java.time.Instant.EPOCH,mock(OutboxEventWriter.class),
            mock(vn.covie.audit.AuditLogWriter.class),mock(vn.covie.shared.web.RequestIdAccessor.class));
    }

    @Test void forgedProviderStatusCannotGrantEntitlement() throws Exception {
        var response=json.readTree("""
            {"code":"00","data":{"status":"PAID","amount":10000,"amountPaid":10000,
             "orderCode":4000000001,"id":"p1","transactions":[{"reference":"ref1","amount":10000}]}}
            """);
        assertThrows(RuntimeException.class,()->service().recoverVerifiedProviderPayment(response));
    }

    @Test void mismatchedPaidAmountIsHeldForReview() throws Exception {
        when(gateway.verifyResponseSignature(any())).thenReturn(true);
        var response=json.readTree("""
            {"code":"00","data":{"status":"PAID","amount":10000,"amountPaid":5000,
             "orderCode":4000000001,"id":"p1","transactions":[{"reference":"ref1","amount":10000}]}}
            """);
        assertEquals("review",service().recoverVerifiedProviderPayment(response));
    }

    @Test void ambiguousPaymentsAreNotAutoSettled() throws Exception {
        when(gateway.verifyResponseSignature(any())).thenReturn(true);
        var response=json.readTree("""
            {"code":"00","data":{"status":"PAID","amount":10000,"amountPaid":10000,
             "orderCode":4000000001,"id":"p1","transactions":[
                {"reference":"ref1","amount":5000},{"reference":"ref2","amount":5000}]}}
            """);
        assertEquals("review",service().recoverVerifiedProviderPayment(response));
    }

    @Test void onlyPaidLinksCanBeRecovered() throws Exception {
        when(gateway.verifyResponseSignature(any())).thenReturn(true);
        var response=json.readTree("""
            {"code":"00","data":{"status":"PENDING","amount":10000,"amountPaid":0,
             "orderCode":4000000001,"id":"p1","transactions":[]}}
            """);
        assertEquals("not-paid",service().recoverVerifiedProviderPayment(response));
    }
}
