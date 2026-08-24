package vn.covie.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class RateLimitFingerprintService {
    private final RateLimitProperties properties;

    public RateLimitFingerprintService(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String email(String normalizedEmail) {
        return hmac(normalizedEmail);
    }

    public String opaqueCredential(String rawCredential) {
        return hmac(rawCredential);
    }

    public String ip(String normalizedIp) {
        return hmac(normalizedIp == null ? "unknown" : normalizedIp.trim());
    }

    private String hmac(String value) {
        byte[] key = keyMaterial();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize rate-limit fingerprinting.", ex);
        }
    }

    byte[] keyMaterial() {
        String configured = properties.hmacKey();
        if (configured == null) return new byte[0];
        if (configured.startsWith("base64:")) {
            try {
                return Base64.getDecoder().decode(configured.substring("base64:".length()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid rate-limit key encoding.");
            }
        }
        return configured.getBytes(StandardCharsets.UTF_8);
    }
}
