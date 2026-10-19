package vn.covie.billing.support;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import vn.covie.billing.payos.PayosGateway;
import vn.covie.billing.payos.PayosSignature;

public final class LoopbackPayosProvider implements AutoCloseable {
    private static final String CLIENT = "loopback-client-only";
    private static final String API_KEY = "loopback-api-only";
    private final ObjectMapper json;
    private final String checksum;
    private final String controlToken = UUID.randomUUID().toString();
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService requests = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    private final Map<String, Runnable> actions = new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    public final AtomicInteger creations = new AtomicInteger();
    public final AtomicInteger lookups = new AtomicInteger();
    public final AtomicBoolean loseNextCreateResponse = new AtomicBoolean();
    public final AtomicBoolean tamperNextLookupSignature = new AtomicBoolean();
    public final AtomicReference<Runnable> nextLookupAction = new AtomicReference<>();
    public final AtomicReference<java.util.function.Consumer<ObjectNode>> nextLookupData = new AtomicReference<>();

    private static final class Payment {
        final long orderCode;
        final long amount;
        final String link;
        volatile boolean paid;
        Payment(long orderCode, long amount) {
            this.orderCode = orderCode; this.amount = amount;
            this.link = String.format("%032x", orderCode);
        }
    }

    public LoopbackPayosProvider(ObjectMapper json, String checksum) throws Exception {
        this.json = json; this.checksum = checksum;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/payment-requests", this::provider);
        server.createContext("/_fixture/paid/", this::control);
        server.createContext("/_fixture/action/", this::action);
        server.createContext("/_fixture/stats", this::action);
        server.setExecutor(requests);
        server.start();
    }

    public String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public String controlToken() { return controlToken; }
    public void registerAction(String name, Runnable action) { actions.put(name, action); }
    public void assertHealthy() { assertThat(failure.get()).as("owned provider request validation").isNull(); }
    @Override public void close() { server.stop(0); requests.shutdownNow(); }

    public PayosGateway gateway(PayosSignature signatures) throws Exception {
        HttpClient local = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpClient relay = mock(HttpClient.class);
        doAnswer(invocation -> {
            HttpRequest original = invocation.getArgument(0);
            assertThat(original.uri().getScheme()).isEqualTo("https");
            assertThat(original.uri().getHost()).isEqualTo("api-merchant.payos.vn");
            assertThat(original.uri().getRawPath()).matches("/v2/payment-requests(?:/[0-9]+)?");
            var forwarded = HttpRequest.newBuilder(URI.create(origin() + original.uri().getRawPath()))
                .timeout(Duration.ofSeconds(5));
            original.headers().map().forEach((name, values) -> values.forEach(value -> forwarded.header(name, value)));
            forwarded.method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
            HttpResponse.BodyHandler<String> handler = invocation.getArgument(1);
            return local.send(forwarded.build(), handler);
        }).when(relay).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(relay);
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            return new PayosGateway(CLIENT, API_KEY, checksum, "https://billing.invalid", false, signatures, json) {
                @Override public boolean isEnabled() { return true; }
            };
        }
    }

    public void markPaid(long orderCode) {
        Payment payment = payments.get(orderCode);
        if (payment == null) throw new IllegalArgumentException("Unknown fixture payment");
        payment.paid = true;
    }

    public ObjectNode callback(long orderCode) throws Exception {
        Payment payment = payments.get(orderCode);
        if (payment == null || !payment.paid) throw new IllegalArgumentException("Fixture payment is not paid");
        return signed(json.createObjectNode().put("orderCode", orderCode).put("amount", payment.amount)
            .put("paymentLinkId", payment.link).put("currency", "VND").put("code", "00")
            .put("reference", "loopback-reference-" + orderCode));
    }

    private void control(HttpExchange exchange) {
        try (exchange) {
            String supplied = exchange.getRequestHeaders().getFirst("x-fixture-control");
            if (supplied == null || !MessageDigest.isEqual(controlToken.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                send(exchange, 403, json.createObjectNode().put("error", "Fixture control denied"));
                return;
            }
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            long code = Long.parseLong(exchange.getRequestURI().getPath().substring("/_fixture/paid/".length()));
            markPaid(code);
            send(exchange, 200, callback(code));
        } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
    }

    private void action(HttpExchange exchange) {
        try (exchange) {
            String supplied = exchange.getRequestHeaders().getFirst("x-fixture-control");
            if (supplied == null || !MessageDigest.isEqual(controlToken.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                send(exchange, 403, json.createObjectNode().put("error", "Fixture control denied"));
                return;
            }
            if (exchange.getRequestURI().getPath().equals("/_fixture/stats")) {
                assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                ObjectNode stats = json.createObjectNode().put("creates", creations.get()).put("lookups", lookups.get());
                var records = stats.putArray("payments");
                payments.values().stream().sorted(java.util.Comparator.comparingLong(value -> value.orderCode))
                    .forEach(payment -> records.addObject().put("orderCode", payment.orderCode)
                        .put("amount", payment.amount).put("paid", payment.paid));
                send(exchange, 200, stats);
                return;
            }
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            String name = exchange.getRequestURI().getPath().substring("/_fixture/action/".length());
            Runnable action = actions.get(name);
            if (action == null) {
                send(exchange, 404, json.createObjectNode().put("error", "Unknown fixture action"));
                return;
            }
            action.run();
            send(exchange, 200, json.createObjectNode().put("completed", true));
        } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
    }

    private void provider(HttpExchange exchange) {
        try (exchange) {
            assertThat(exchange.getRequestHeaders().getFirst("x-client-id")).isEqualTo(CLIENT);
            assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo(API_KEY);
            Payment payment;
            if ("POST".equals(exchange.getRequestMethod())) {
                creations.incrementAndGet();
                assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v2/payment-requests");
                JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
                long code = request.path("orderCode").asLong();
                long amount = request.path("amount").asLong();
                assertThat(code).isPositive(); assertThat(amount).isPositive();
                var fields = new TreeMap<String, String>();
                for (String key : java.util.List.of("amount", "cancelUrl", "description", "orderCode", "returnUrl")) {
                    fields.put(key, request.path(key).asText());
                }
                assertThat(request.path("signature").asText()).isEqualTo(hmac(canonical(fields)));
                assertThat(request.toString()).doesNotContain(CLIENT, API_KEY, checksum);
                payment = payments.computeIfAbsent(code, ignored -> new Payment(code, amount));
                assertThat(payment.amount).isEqualTo(amount);
                if (loseNextCreateResponse.getAndSet(false)) {
                    send(exchange, 503, json.createObjectNode().put("error", "Fixture response unavailable"));
                    return;
                }
                send(exchange, 200, signed(json.createObjectNode().put("orderCode", code).put("amount", amount)
                    .put("paymentLinkId", payment.link).put("checkoutUrl", "https://pay.payos.vn/web/" + payment.link)
                    .put("qrCode", "fixture-qr")));
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                lookups.incrementAndGet();
                Runnable lookupAction = nextLookupAction.getAndSet(null);
                if (lookupAction != null) lookupAction.run();
                long code = Long.parseLong(exchange.getRequestURI().getPath().substring("/v2/payment-requests/".length()));
                payment = payments.get(code);
                if (payment == null) {
                    send(exchange, 404, json.createObjectNode().put("error", "Unknown fixture payment"));
                    return;
                }
                ObjectNode data = json.createObjectNode().put("orderCode", code).put("amount", payment.amount)
                    .put("amountPaid", payment.paid ? payment.amount : 0).put("id", payment.link)
                    .put("status", payment.paid ? "PAID" : "PENDING");
                var transactions = data.putArray("transactions");
                if (payment.paid) transactions.addObject().put("amount", payment.amount)
                    .put("reference", "loopback-reference-" + code);
                var customize = nextLookupData.getAndSet(null);
                if (customize != null) customize.accept(data);
                ObjectNode response = signed(data);
                if (tamperNextLookupSignature.getAndSet(false)) response.put("signature", "invalid-fixture-signature");
                send(exchange, 200, response);
            }
        } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
    }

    public ObjectNode signed(ObjectNode data) throws Exception {
        var fields = new TreeMap<String, String>();
        data.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue().isValueNode()
            ? entry.getValue().asText() : entry.getValue().toString()));
        return json.createObjectNode().put("code", "00").put("success", true)
            .put("signature", hmac(canonical(fields))).set("data", data);
    }

    private String canonical(TreeMap<String, String> fields) {
        return fields.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining("&"));
    }

    private String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(checksum.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private void send(HttpExchange exchange, int status, JsonNode body) throws Exception {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
