package vn.covie.billing.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingReconciliationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID id = UUID.randomUUID();
    private final BillingReconciliationService.Candidate candidate =
        new BillingReconciliationService.Candidate(id, 4200000001L, 10000L, "pay-link-123");

    private BillingReconciliationService.Observation observed(String data) throws Exception {
        return BillingReconciliationService.compare(candidate, mapper.readTree(data));
    }

    @Test void flagsPaidWithoutWebhookInsteadOfSettlingAutomatically() throws Exception {
        assertEquals("PAID_PENDING_WEBHOOK", observed("""
          {"code":"00","data":{"orderCode":4200000001,"amount":10000,"id":"pay-link-123",
           "amountPaid":10000,"status":"PAID"}}
          """).discrepancy());
    }
    @Test void rejectsWrongLinkOrAmountAndUnknownResponse() throws Exception {
        assertEquals("LINK_MISMATCH", observed("""
          {"code":"00","data":{"orderCode":4200000001,"amount":10000,"id":"wrong","status":"PAID"}}
          """).discrepancy());
        assertEquals("AMOUNT_MISMATCH", observed("""
          {"code":"00","data":{"orderCode":4200000001,"amount":9000,"id":"pay-link-123","status":"PAID"}}
          """).discrepancy());
        assertEquals("PROVIDER_FAILURE", observed("""
          {"code":"01","data":{}}
          """).discrepancy());
    }
    @Test void pendingAndCancelledAreNotPaid() throws Exception {
        assertEquals("NONE", observed("""
          {"code":"00","data":{"orderCode":4200000001,"amount":10000,"id":"pay-link-123","status":"PENDING"}}
          """).discrepancy());
        assertEquals("NONE", observed("""
          {"code":"00","data":{"orderCode":4200000001,"amount":10000,"id":"pay-link-123","status":"CANCELLED"}}
          """).discrepancy());
    }
}
