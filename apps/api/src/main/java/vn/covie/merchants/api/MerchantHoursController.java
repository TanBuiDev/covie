package vn.covie.merchants.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.merchants.api.dto.MerchantHoursResponse;
import vn.covie.merchants.api.dto.ReplaceLocationHoursRequest;
import vn.covie.merchants.application.MerchantHoursService;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/locations/{locationId}/hours")
public class MerchantHoursController {
    private final MerchantHoursService hours;
    public MerchantHoursController(MerchantHoursService hours) { this.hours = hours; }

    @GetMapping
    public MerchantHoursResponse get(@PathVariable UUID merchantId, @PathVariable UUID locationId) {
        return hours.get(merchantId, locationId);
    }

    @PutMapping
    public MerchantHoursResponse replace(@PathVariable UUID merchantId, @PathVariable UUID locationId,
            @Valid @RequestBody ReplaceLocationHoursRequest request) {
        return hours.replace(merchantId, locationId, request);
    }
}
