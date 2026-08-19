package vn.covie.delivery.api;

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
import vn.covie.delivery.api.dto.DeliveryCoverageResponse;
import vn.covie.delivery.api.dto.DeliveryCoverageUpsertRequest;
import vn.covie.delivery.application.DeliveryCoverageService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages")
public class MerchantDeliveryCoverageController {
    private final DeliveryCoverageService service;
    public MerchantDeliveryCoverageController(DeliveryCoverageService service) { this.service = service; }
    @GetMapping public List<DeliveryCoverageResponse> list(@PathVariable UUID merchantId, @PathVariable UUID locationId) { return service.list(merchantId, locationId); }
    @PostMapping public ResponseEntity<DeliveryCoverageResponse> create(@PathVariable UUID merchantId, @PathVariable UUID locationId, @RequestBody DeliveryCoverageUpsertRequest request) {
        var response = service.create(merchantId, locationId, request);
        return ResponseEntity.status(201).eTag(VersionPrecondition.format(response.version())).body(response);
    }
    @PatchMapping("/{coverageId}") public ResponseEntity<DeliveryCoverageResponse> update(@PathVariable UUID merchantId, @PathVariable UUID locationId, @PathVariable UUID coverageId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody DeliveryCoverageUpsertRequest request) {
        var response = service.update(merchantId, locationId, coverageId, ifMatch, request);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(response.version())).body(response);
    }
    @DeleteMapping("/{coverageId}") public ResponseEntity<Void> delete(@PathVariable UUID merchantId, @PathVariable UUID locationId, @PathVariable UUID coverageId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(merchantId, locationId, coverageId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
