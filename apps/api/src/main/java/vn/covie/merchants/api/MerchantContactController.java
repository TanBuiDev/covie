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
import vn.covie.merchants.api.dto.MerchantContactRequest;
import vn.covie.merchants.api.dto.MerchantContactResponse;
import vn.covie.merchants.application.MerchantContactService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/contacts")
public class MerchantContactController {
    private final MerchantContactService contacts;
    public MerchantContactController(MerchantContactService contacts) { this.contacts = contacts; }

    @GetMapping
    public List<MerchantContactResponse> list(@PathVariable UUID merchantId) { return contacts.list(merchantId); }

    @Operation(responses = @ApiResponse(responseCode = "200", headers = @Header(name = HttpHeaders.ETAG)))
    @GetMapping("/{contactId}")
    public ResponseEntity<MerchantContactResponse> get(
            @PathVariable UUID merchantId, @PathVariable UUID contactId) {
        MerchantContactResponse contact = contacts.get(merchantId, contactId);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(contact.version())).body(contact);
    }

    @PostMapping
    public ResponseEntity<MerchantContactResponse> create(@PathVariable UUID merchantId,
            @Valid @RequestBody MerchantContactRequest request) {
        MerchantContactResponse contact = contacts.create(merchantId, request);
        return ResponseEntity.status(201).eTag(VersionPrecondition.format(contact.version())).body(contact);
    }

    @PatchMapping("/{contactId}")
    public ResponseEntity<MerchantContactResponse> update(@PathVariable UUID merchantId, @PathVariable UUID contactId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody MerchantContactRequest request) {
        MerchantContactResponse contact = contacts.update(merchantId, contactId, ifMatch, request);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(contact.version())).body(contact);
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> delete(@PathVariable UUID merchantId, @PathVariable UUID contactId) {
        contacts.delete(merchantId, contactId);
        return ResponseEntity.noContent().build();
    }
}
