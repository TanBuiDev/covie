package vn.covie.billing.payos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Credentials and checksum are only used on the server. */
@Service
public class PayosGateway {
    private final String clientId, apiKey, checksumKey, appUrl;
    private final boolean enabled;
    private final PayosSignature signatures;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PayosGateway(@Value("${PAYOS_CLIENT_ID:}") String clientId,
                        @Value("${PAYOS_API_KEY:}") String apiKey,
                        @Value("${PAYOS_CHECKSUM_KEY:}") String checksumKey,
                        @Value("${COVIE_PUBLIC_APP_URL:}") String appUrl,
                        @Value("${PAYOS_PAYMENT_ENABLED:false}") boolean enabled,
                        PayosSignature signatures, ObjectMapper mapper) {
        this.clientId = clientId; this.apiKey = apiKey; this.checksumKey = checksumKey;
        this.appUrl = appUrl; this.enabled = enabled; this.signatures = signatures; this.mapper = mapper;
    }

    public boolean isEnabled() {
        if (!enabled || clientId.isBlank() || apiKey.isBlank() || checksumKey.isBlank()) return false;
        try {
            URI uri = URI.create(appUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && !appUrl.endsWith("/");
        } catch (IllegalArgumentException ex) { return false; }
    }

    public boolean canVerifyWebhook() { return enabled && !checksumKey.isBlank(); }
    public String checksumKey() {
        if (!canVerifyWebhook()) throw new ProviderUnavailableException("payOS not configured");
        return checksumKey;
    }

    public PaymentLink createPaymentLink(long orderCode, long amount, String description, long expiresAt) {
        if (!isEnabled()) throw new ProviderUnavailableException("payOS not configured");
        String cancelUrl = appUrl + "/seller/billing/return?cancel=true";
        String returnUrl = appUrl + "/seller/billing/return";
        String signature = signatures.signPaymentLink(amount, cancelUrl, description, orderCode, returnUrl, checksumKey);
        var payload = Map.of("orderCode", orderCode, "amount", amount,
            "description", description, "cancelUrl", cancelUrl,
            "returnUrl", returnUrl, "expiredAt", expiresAt, "signature", signature);
        JsonNode response = request("POST", "/v2/payment-requests", payload);
        JsonNode data = response.path("data");
        if (!"00".equals(response.path("code").asText())
            || data.path("orderCode").asLong(-1) != orderCode
            || data.path("amount").asLong(-1) != amount) {
            throw new ProviderUnavailableException("Mismatched payOS checkout response");
        }
        String url = data.path("checkoutUrl").asText("");
        String linkId = data.path("paymentLinkId").asText("");
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || !(uri.getHost().equals("pay.payos.vn") || uri.getHost().endsWith(".payos.vn"))
                || uri.getUserInfo() != null || linkId.isBlank()) {
                throw new ProviderUnavailableException("Unsafe payOS checkout URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new ProviderUnavailableException("Invalid payOS checkout URL");
        }
        return new PaymentLink(orderCode, linkId, url, data.path("qrCode").asText(""));
    }

    public JsonNode lookup(long orderCode) {
        if (!isEnabled()) throw new ProviderUnavailableException("payOS not configured");
        return request("GET", "/v2/payment-requests/" + orderCode, null);
    }

    private JsonNode request(String method, String path, Object payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api-merchant.payos.vn" + path))
                .timeout(Duration.ofSeconds(12)).header("x-client-id", clientId)
                .header("x-api-key", apiKey).header("Accept", "application/json");
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
            } else builder.GET();
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 100_000) {
                throw new ProviderUnavailableException("payOS HTTP failure " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (ProviderUnavailableException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("payOS interrupted");
        } catch (Exception ex) { throw new ProviderUnavailableException("payOS request unavailable"); }
    }
    public record PaymentLink(long orderCode, String paymentLinkId, String checkoutUrl, String qrPayload) {}
    public static class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message) { super(message); }
    }
}
