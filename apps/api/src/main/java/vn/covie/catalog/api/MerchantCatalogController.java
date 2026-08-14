package vn.covie.catalog.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.catalog.api.dto.CatalogItemManagementResponse;
import vn.covie.catalog.api.dto.create.CreateCatalogItemRequest;
import vn.covie.catalog.api.dto.edit.CreateVariantRequest;
import vn.covie.catalog.api.dto.edit.UpdateCatalogItemRequest;
import vn.covie.catalog.api.dto.edit.UpdateLocationOfferRequest;
import vn.covie.catalog.api.dto.edit.UpdateVariantRequest;
import vn.covie.catalog.application.CatalogCreateService;
import vn.covie.catalog.application.CatalogEditService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/catalog-items")
public class MerchantCatalogController {
    private final CatalogCreateService catalog;
    private final CatalogEditService edits;
    public MerchantCatalogController(CatalogCreateService catalog, CatalogEditService edits) { this.catalog = catalog; this.edits = edits; }
    @PostMapping
    public ResponseEntity<CatalogItemManagementResponse> create(@PathVariable UUID merchantId,
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            @Valid @RequestBody CreateCatalogItemRequest request) {
        var execution = catalog.create(merchantId, idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/merchants/" + merchantId + "/catalog-items/" + execution.response().id()))
                .body(execution.response());
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<Void> updateItem(@PathVariable UUID merchantId, @PathVariable UUID itemId,
            @RequestHeader(value="If-Match", required=false) String ifMatch, @Valid @RequestBody UpdateCatalogItemRequest request) {
        var result = edits.updateItem(merchantId, itemId, ifMatch, request);
        return ResponseEntity.noContent().eTag(VersionPrecondition.format(result.version())).build();
    }

    @PatchMapping("/{itemId}/variants/{variantId}")
    public ResponseEntity<Void> updateVariant(@PathVariable UUID merchantId, @PathVariable UUID itemId, @PathVariable UUID variantId,
            @RequestHeader(value="If-Match", required=false) String ifMatch, @Valid @RequestBody UpdateVariantRequest request) {
        var result = edits.updateVariant(merchantId, itemId, variantId, ifMatch, request);
        return ResponseEntity.noContent().eTag(VersionPrecondition.format(result.version())).build();
    }

    @PostMapping("/{itemId}/variants")
    public ResponseEntity<Void> createVariant(@PathVariable UUID merchantId, @PathVariable UUID itemId,
            @Valid @RequestBody CreateVariantRequest request) {
        var result = edits.createVariant(merchantId, itemId, request);
        return ResponseEntity.created(URI.create("/api/v1/merchants/" + merchantId + "/catalog-items/" + itemId + "/variants/" + result.id()))
                .eTag(VersionPrecondition.format(result.version())).build();
    }

    @DeleteMapping("/{itemId}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable UUID merchantId, @PathVariable UUID itemId, @PathVariable UUID variantId,
            @RequestHeader(value="If-Match", required=false) String ifMatch) {
        var result = edits.deleteVariant(merchantId, itemId, variantId, ifMatch);
        return ResponseEntity.noContent().eTag(VersionPrecondition.format(result.version())).build();
    }

    @PatchMapping("/{itemId}/location-offers/{locationVariantId}")
    public ResponseEntity<Void> updateLocationOffer(@PathVariable UUID merchantId, @PathVariable UUID itemId,
            @PathVariable UUID locationVariantId, @RequestHeader(value="If-Match", required=false) String ifMatch,
            @Valid @RequestBody UpdateLocationOfferRequest request) {
        var result = edits.updateLocationOffer(merchantId, itemId, locationVariantId, ifMatch, request);
        return ResponseEntity.noContent().eTag(VersionPrecondition.format(result.version())).build();
    }
}
