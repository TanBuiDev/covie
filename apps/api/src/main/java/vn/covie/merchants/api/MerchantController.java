package vn.covie.merchants.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.merchants.api.dto.MerchantManagementResponse;
import vn.covie.merchants.api.dto.UpdateMerchantRequest;
import vn.covie.merchants.application.MerchantApplicationService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}")
public class MerchantController {
    private final MerchantApplicationService merchants;

    public MerchantController(MerchantApplicationService merchants) { this.merchants = merchants; }

    @GetMapping
    public ResponseEntity<MerchantManagementResponse> get(@PathVariable UUID merchantId) {
        return response(merchants.get(merchantId));
    }

    @PatchMapping
    public ResponseEntity<MerchantManagementResponse> update(@PathVariable UUID merchantId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateMerchantRequest request) {
        return response(merchants.update(merchantId, ifMatch, request));
    }

    private static ResponseEntity<MerchantManagementResponse> response(MerchantManagementResponse body) {
        return ResponseEntity.ok().eTag(VersionPrecondition.format(body.version()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }
}
