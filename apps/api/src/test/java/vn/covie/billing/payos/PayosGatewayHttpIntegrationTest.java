package vn.covie.billing.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayosGatewayHttpIntegrationTest {
    private static final String CLIENT = "fixture-client-only";
    private static final String API_KEY = "fixture-api-only";
    private static final String CHECKSUM = "fixture-checksum-only";
    private static final String LINK_ID = "1234567890abcdef1234567890abcdef";
    private static final long ORDER = 4_200_000_001L;
    private static final long AMOUNT = 10_000L;
    private final ObjectMapper json = new ObjectMapper();
    private final UUID merchant = UUID.randomUUID();
    private final AtomicReference<String> mode = new AtomicReference<>("VALID");
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private final AtomicInteger posts = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private HttpServer server;
    private PayosGateway gateway;

    @BeforeEach void startOwnedProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/payment-requests", this::provider);
        server.start();
        HttpClient localClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpClient relay = mock(HttpClient.class);
        doAnswer(invocation -> {
            HttpRequest original = invocation.getArgument(0);
            assertThat(original.uri().getScheme()).isEqualTo("https");
            assertThat(original.uri().getHost()).isEqualTo("api-merchant.payos.vn");
            assertThat(original.timeout()).contains(Duration.ofSeconds(12));
            var forwarded = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + original.uri().getRawPath())).timeout(Duration.ofSeconds(5));
            original.headers().map().forEach((name, values) -> values.forEach(value -> forwarded.header(name, value)));
            forwarded.method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
            HttpResponse.BodyHandler<String> handler = invocation.getArgument(1);
            return localClient.send(forwarded.build(), handler);
        }).when(relay).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(relay);
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            gateway = new PayosGateway(CLIENT, API_KEY, CHECKSUM, "https://billing.invalid", false,
                new PayosSignature(json), json) {
                @Override public boolean isEnabled() { return true; }
            };
        }
    }

    @AfterEach void stopOwnedProvider() {
        if (server != null) server.stop(0);
        assertThat(serverFailure.get()).as("owned fake provider validated the actual request").isNull();
    }

    private void provider(HttpExchange exchange) {
        try (exchange) {
            assertThat(exchange.getRequestHeaders().getFirst("x-client-id")).isEqualTo(CLIENT);
            assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo(API_KEY);
            assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/json");
            boolean create = "POST".equals(exchange.getRequestMethod());
            if (create) {
                posts.incrementAndGet();
                assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v2/payment-requests");
                assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
                JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(request.path("orderCode").asLong()).isEqualTo(ORDER);
                assertThat(request.path("amount").asLong()).isEqualTo(AMOUNT);
                assertThat(request.path("description").asText()).isEqualTo("CVTEST");
                String cancel = "https://billing.invalid/seller/billing/return?merchantId=" + merchant + "&cancel=true";
                String success = "https://billing.invalid/seller/billing/return?merchantId=" + merchant;
                assertThat(request.path("cancelUrl").asText()).isEqualTo(cancel);
                assertThat(request.path("returnUrl").asText()).isEqualTo(success);
                assertThat(request.path("expiredAt").asLong()).isEqualTo(1_900_000_000L);
                assertThat(request.path("signature").asText()).isEqualTo(hmac("amount=" + AMOUNT
                    + "&cancelUrl=" + cancel + "&description=CVTEST&orderCode=" + ORDER + "&returnUrl=" + success));
                assertThat(request.toString()).doesNotContain(API_KEY, CLIENT, CHECKSUM);
            } else {
                gets.incrementAndGet();
                assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v2/payment-requests/" + ORDER);
                assertThat(exchange.getRequestBody().readAllBytes()).isEmpty();
            }
            if ("UNAVAILABLE".equals(mode.get())) {
                send(exchange, 503, "fixture-private-provider-detail");
                return;
            }
            ObjectNode data = json.createObjectNode().put("orderCode", ORDER).put("amount", AMOUNT);
            if (create) {
                data.put("paymentLinkId", LINK_ID).put("checkoutUrl", "https://pay.payos.vn/web/" + LINK_ID)
                    .put("qrCode", "fixture-qr");
            } else {
                data.put("id", LINK_ID).put("status", "PAID").put("amountPaid", AMOUNT);
                data.putArray("transactions").addObject().put("reference", "fixture-reference").put("amount", AMOUNT);
            }
            if ("WRONG_ORDER".equals(mode.get())) data.put("orderCode", ORDER + 1);
            if ("WRONG_AMOUNT".equals(mode.get())) data.put("amount", AMOUNT + 1);
            if ("UNSAFE_URL".equals(mode.get())) data.put("checkoutUrl", "https://payment.invalid/unsafe");
            String signature = signData(data);
            if ("BAD_SIGNATURE".equals(mode.get())) signature = "0".repeat(64);
            ObjectNode response = json.createObjectNode().put("code", "00").put("signature", signature).set("data", data);
            send(exchange, 200, json.writeValueAsString(response));
        } catch (Throwable failure) {
            serverFailure.compareAndSet(null, failure);
        }
    }

    private void send(HttpExchange exchange, int status, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String signData(JsonNode data) throws Exception {
        var fields = new TreeMap<String, String>();
        data.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue().isValueNode()
            ? entry.getValue().asText() : entry.getValue().toString()));
        return hmac(fields.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining("&")));
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CHECKSUM.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void realRequestSerializationHeadersAndSignaturesSurviveTheOwnedHttpRoundTrip() {
        var result = gateway.createPaymentLink(merchant, ORDER, AMOUNT, "CVTEST", 1_900_000_000L);
        assertThat(result.orderCode()).isEqualTo(ORDER);
        assertThat(result.paymentLinkId()).isEqualTo(LINK_ID);
        assertThat(result.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/" + LINK_ID);
        assertThat(result.qrPayload()).isEqualTo("fixture-qr");
        JsonNode response = gateway.lookup(ORDER);
        assertThat(gateway.verifyResponseSignature(response)).isTrue();
        assertThat(response.path("data").path("status").asText()).isEqualTo("PAID");
        assertThat(posts).hasValue(1);
        assertThat(gets).hasValue(1);
    }

    @Test void forgedOrSignedButMismatchedAndUnsafeCreateResponsesAreRejected() {
        for (String responseMode : java.util.List.of("BAD_SIGNATURE", "WRONG_ORDER", "WRONG_AMOUNT", "UNSAFE_URL")) {
            mode.set(responseMode);
            assertThrows(PayosGateway.ProviderUnavailableException.class,
                () -> gateway.createPaymentLink(merchant, ORDER, AMOUNT, "CVTEST", 1_900_000_000L));
        }
        assertThat(posts).hasValue(4);
    }

    @Test void successfulHttpStatusDoesNotMakeForgedLookupDataVerified() {
        mode.set("BAD_SIGNATURE");
        JsonNode response = gateway.lookup(ORDER);
        assertThat(response.path("data").path("status").asText()).isEqualTo("PAID");
        assertThat(gateway.verifyResponseSignature(response)).isFalse();
        assertThat(gets).hasValue(1);
    }

    @Test void providerFailureRetainsNoRawResponseInThePublicException() {
        mode.set("UNAVAILABLE");
        var failure = assertThrows(PayosGateway.ProviderUnavailableException.class, () -> gateway.lookup(ORDER));
        assertThat(failure.getMessage()).doesNotContain("fixture-private-provider-detail", API_KEY, CHECKSUM);
        assertThat(gets).hasValue(1);
    }
}
