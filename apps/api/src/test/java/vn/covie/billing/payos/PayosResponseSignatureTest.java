package vn.covie.billing.payos;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** payOS response signatures must be accepted even if new checkout creation is paused. */
class PayosResponseSignatureTest {
    @Test void validatesAuthenticatedProviderDataAndRejectsMutations() throws Exception {
        var json=new ObjectMapper();
        var service=new PayosGateway("test-client","test-api","test-only-secret",
            "https://example.test",false,new PayosSignature(json),json);
        var response=json.readTree("""
            {"data":{"orderCode":42,"currency":"VND","amount":10000},
             "signature":"9392415c1f02c708279bef218fd192a8652da4db16c1778ac73e421d0e3d58be"}
            """);
        assertTrue(service.verifyResponseSignature(response));
        var forged=json.readTree("""
            {"data":{"orderCode":42,"currency":"VND","amount":1},
             "signature":"9392415c1f02c708279bef218fd192a8652da4db16c1778ac73e421d0e3d58be"}
            """);
        assertFalse(service.verifyResponseSignature(forged));
        assertFalse(service.isEnabled()); // operator never authorized new checkout
    }
}
