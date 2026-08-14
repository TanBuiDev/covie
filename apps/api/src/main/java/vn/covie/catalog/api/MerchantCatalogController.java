package vn.covie.catalog.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.catalog.api.dto.CatalogItemManagementResponse;
import vn.covie.catalog.api.dto.create.CreateCatalogItemRequest;
import vn.covie.catalog.application.CatalogCreateService;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/catalog-items")
public class MerchantCatalogController {
    private final CatalogCreateService catalog;
    public MerchantCatalogController(CatalogCreateService catalog) { this.catalog = catalog; }
    @PostMapping
    public ResponseEntity<CatalogItemManagementResponse> create(@PathVariable UUID merchantId,
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            @Valid @RequestBody CreateCatalogItemRequest request) {
        var execution = catalog.create(merchantId, idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/merchants/" + merchantId + "/catalog-items/" + execution.response().id()))
                .body(execution.response());
    }
}
