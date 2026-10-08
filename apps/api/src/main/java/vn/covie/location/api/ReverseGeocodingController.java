package vn.covie.location.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.location.api.dto.ReverseGeocodingResponse;
import vn.covie.location.application.ReverseGeocodingService;

@RestController
public class ReverseGeocodingController {
    private final ReverseGeocodingService geocoding;
    public ReverseGeocodingController(ReverseGeocodingService geocoding) { this.geocoding = geocoding; }

    @GetMapping("/api/v1/me/addresses/reverse-geocode")
    public ResponseEntity<ReverseGeocodingResponse> reverse(@RequestParam double latitude, @RequestParam double longitude) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(geocoding.reverse(latitude, longitude));
    }
}
