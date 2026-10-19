package vn.covie.billing.payos;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PayosSignatureTest {
    private final PayosSignature signer = new PayosSignature(new ObjectMapper());

    @Test void sortsWebhookFields() throws Exception {
        var data = new ObjectMapper().readTree("{\"reference\":\"TX1\",\"amount\":10000,\"description\":null,\"orderCode\":42}");
        assertEquals("amount=10000&description=&orderCode=42&reference=TX1", signer.canonicalWebhookData(data));
    }
    @Test void rejectsForgedSignatures() throws Exception {
        var data = new ObjectMapper().readTree("{\"amount\":10000}");
        assertFalse(signer.verifyWebhook(data, "bad", "secret"));
        assertFalse(signer.verifyWebhook(data, "0".repeat(64), "secret"));
    }
    @Test void verifiesSignedWebhookAndRejectsTampering() throws Exception {
        var data = new ObjectMapper().readTree("{\"orderCode\":42,\"currency\":\"VND\",\"amount\":10000}");
        assertTrue(signer.verifyWebhook(data,
            "9392415c1f02c708279bef218fd192a8652da4db16c1778ac73e421d0e3d58be",
            "test-only-secret"));
        var changed = new ObjectMapper().readTree("{\"orderCode\":42,\"currency\":\"VND\",\"amount\":10001}");
        assertFalse(signer.verifyWebhook(changed,
            "9392415c1f02c708279bef218fd192a8652da4db16c1778ac73e421d0e3d58be",
            "test-only-secret"));
    }

    @Test void paymentRequestSignatureChangesWithAmount() {
        assertNotEquals(
            signer.signPaymentLink(10000, "https://a.vn/c", "COVIE", 123, "https://a.vn/r", "secret"),
            signer.signPaymentLink(10001, "https://a.vn/c", "COVIE", 123, "https://a.vn/r", "secret"));
    }
}
