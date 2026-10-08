package vn.covie.location.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.location.api.dto.ReverseGeocodingResponse;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Explicit, user-triggered Nominatim-compatible lookup. Disabled until endpoint is configured. */
@Service
public class ReverseGeocodingService {
    private record Cached(ReverseGeocodingResponse response, long expiresAt) {}
    private final String endpoint;
    private final String userAgent;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private long lastAttempt;

    public ReverseGeocodingService(
            @Value("${COVIE_GEOCODING_REVERSE_URL:}") String endpoint,
            @Value("${COVIE_GEOCODING_USER_AGENT:CoVie/1.0 (https://github.com/TanBuiDev/covie)}") String userAgent,
            ObjectMapper mapper) {
        this.endpoint = endpoint.trim(); this.userAgent = userAgent; this.mapper = mapper;
    }

    public synchronized ReverseGeocodingResponse reverse(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Coordinates are invalid.");
        }
        if (endpoint.isBlank()) throw unavailable();
        String key = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude);
        long now = System.nanoTime();
        Cached saved = cache.get(key);
        if (saved != null && now < saved.expiresAt()) return saved.response();
        if (lastAttempt != 0 && now - lastAttempt < Duration.ofSeconds(1).toNanos()) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "Please wait before another address lookup.");
        }
        lastAttempt = now;
        try {
            URI base = URI.create(endpoint);
            if (!"https".equals(base.getScheme()) && !("http".equals(base.getScheme()) && "localhost".equals(base.getHost()))) throw unavailable();
            if (base.getQuery() != null || base.getFragment() != null || base.getUserInfo() != null) throw unavailable();
            URI uri = URI.create(endpoint + "?format=jsonv2&addressdetails=0&accept-language=vi&lat=" + latitude + "&lon=" + longitude);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw unavailable();
            byte[] body = response.body();
            if (body.length > 16_384) throw unavailable();
            String address = mapper.readTree(body).path("display_name").asText("").trim();
            if (address.isBlank() || address.length() > 1000) throw unavailable();
            var result = new ReverseGeocodingResponse(address, "© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright");
            cache.put(key, new Cached(result, now + Duration.ofHours(1).toNanos()));
            while (cache.size() > 256) cache.remove(cache.keySet().iterator().next());
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (ApiException known) { throw known;
        } catch (Exception failure) { throw unavailable(); }
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.SERVICE_UNAVAILABLE, "Address lookup unavailable. Enter the address manually.");
    }
}
