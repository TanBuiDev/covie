package vn.covie.billing.payos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** payOS HMAC_SHA256 signs sorted data keys (not the webhook envelope). */
@Component
public class PayosSignature {
    private final ObjectMapper mapper;
    public PayosSignature(ObjectMapper mapper) { this.mapper = mapper; }

    public String signPaymentLink(long amount, String cancelUrl, String description,
                                  long orderCode, String returnUrl, String secret) {
        return sign("amount=" + amount + "&cancelUrl=" + cancelUrl
            + "&description=" + description + "&orderCode=" + orderCode
            + "&returnUrl=" + returnUrl, secret);
    }

    public boolean verifyWebhook(JsonNode data, String signature, String secret) {
        if (data == null || !data.isObject() || signature == null
            || !signature.matches("(?i)[0-9a-f]{64}")) return false;
        return MessageDigest.isEqual(
            HexFormat.of().parseHex(sign(canonicalWebhookData(data), secret)),
            HexFormat.of().parseHex(signature));
    }

    String canonicalWebhookData(JsonNode data) {
        TreeMap<String, String> fields = new TreeMap<>();
        data.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) fields.put(entry.getKey(), "");
            else if (value.isValueNode()) fields.put(entry.getKey(), value.asText());
            else {
                try { fields.put(entry.getKey(), mapper.writeValueAsString(value)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    throw new IllegalArgumentException("Invalid webhook data", ex);
                }
            }
        });
        return fields.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
            .collect(java.util.stream.Collectors.joining("&"));
    }
    private String sign(String value, String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("Checksum key unavailable");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC_SHA256 unavailable", ex);
        }
    }
}
