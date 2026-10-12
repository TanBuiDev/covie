package vn.covie.location.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ApiException;

class ReverseGeocodingServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NON_200", "MALFORMED", "MISSING", "BLANK", "LONG_ADDRESS", "OVERSIZED", "TIMEOUT"})
    void providerFailuresReturnManualEntryFallbackWithoutCaching(String scenario) throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/reverse", exchange -> {
            requests.incrementAndGet();
            try {
                if (scenario.equals("TIMEOUT")) {
                    try { Thread.sleep(5500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                String response = switch (scenario) {
                    case "MALFORMED" -> "not-json";
                    case "MISSING" -> "{}";
                    case "BLANK" -> "{\"display_name\":\"   \"}";
                    case "LONG_ADDRESS" -> "{\"display_name\":\"" + "x".repeat(1001) + "\"}";
                    case "OVERSIZED" -> "{\"display_name\":\"Valid\",\"extra\":\"" + "x".repeat(17000) + "\"}";
                    default -> "{\"display_name\":\"Valid\"}";
                };
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(scenario.equals("NON_200") ? 503 : 200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var service = new ReverseGeocodingService("http://localhost:" + server.getAddress().getPort() + "/reverse", "CoVie-test/1.0", new ObjectMapper());
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) Thread.sleep(1100);
                assertThatThrownBy(() -> service.reverse(10.8, 106.8)).isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getMessage()).contains("Enter the address manually");
                });
            }
            assertThat(requests).hasValue(2);
        } finally { server.stop(0); executor.shutdownNow(); }
    }

    @Test
    void validatesBeforeContactingProviderAndRequiresExplicitConfiguration() {
        var service = new ReverseGeocodingService("", "CoVie-test/1.0", new ObjectMapper());
        assertThatThrownBy(() -> service.reverse(91, 106)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.reverse(Double.NaN, 106)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.reverse(10.8, 106.8)).isInstanceOf(ApiException.class);
    }

    @Test
    void requestsOnceWithIdentificationCachesAndLimitsOtherCoordinates() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reverse", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("User-Agent")).isEqualTo("CoVie-test/1.0");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("lat=10.8", "lon=106.8", "format=jsonv2");
            byte[] body = "{\"display_name\":\"1 đường thử nghiệm, TP.HCM\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var service = new ReverseGeocodingService("http://localhost:" + server.getAddress().getPort() + "/reverse", "CoVie-test/1.0", new ObjectMapper());
            assertThat(service.reverse(10.8, 106.8).addressText()).contains("TP.HCM");
            assertThat(service.reverse(10.8, 106.8).addressText()).contains("TP.HCM");
            assertThat(requests).hasValue(1);
            assertThatThrownBy(() -> service.reverse(10.81, 106.81)).isInstanceOf(ApiException.class);
            assertThat(requests).hasValue(1);
        } finally { server.stop(0); }
    }
}
