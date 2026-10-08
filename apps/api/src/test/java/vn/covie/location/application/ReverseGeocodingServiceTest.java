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
