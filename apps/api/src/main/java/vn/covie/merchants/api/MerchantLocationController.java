package vn.covie.merchants.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.merchants.api.dto.MerchantLocationResponse;
import vn.covie.merchants.api.dto.MerchantLocationUpsertRequest;
import vn.covie.merchants.application.MerchantLocationService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/locations")
public class MerchantLocationController {
    private final MerchantLocationService locations;
    public MerchantLocationController(MerchantLocationService locations) { this.locations = locations; }

    @GetMapping
    public List<MerchantLocationResponse> list(@PathVariable UUID merchantId) { return locations.list(merchantId); }

    @Operation(responses = @ApiResponse(responseCode = "200", headers = @Header(name = HttpHeaders.ETAG)))
    @GetMapping("/{locationId}")
    public ResponseEntity<MerchantLocationResponse> get(@PathVariable UUID merchantId, @PathVariable UUID locationId) {
        MerchantLocationResponse location = locations.get(merchantId, locationId);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(location.version())).body(location);
    }

    @PostMapping
    public ResponseEntity<MerchantLocationResponse> create(@PathVariable UUID merchantId,
            @Valid @RequestBody MerchantLocationUpsertRequest request) {
        MerchantLocationResponse location = locations.create(merchantId, request);
        return ResponseEntity.status(201).eTag(VersionPrecondition.format(location.version())).body(location);
    }

    @PatchMapping("/{locationId}")
    public ResponseEntity<MerchantLocationResponse> update(@PathVariable UUID merchantId,
            @PathVariable UUID locationId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody MerchantLocationUpsertRequest request) {
        MerchantLocationResponse location = locations.update(merchantId, locationId, ifMatch, request);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(location.version())).body(location);
    }

    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID merchantId, @PathVariable UUID locationId) {
        locations.delete(merchantId, locationId);
        return ResponseEntity.noContent().build();
    }
}
