package vn.covie.merchants.api;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.merchants.api.dto.AddMerchantMemberRequest;
import vn.covie.merchants.api.dto.MerchantMemberResponse;
import vn.covie.merchants.api.dto.UpdateMerchantMemberRequest;
import vn.covie.merchants.api.dto.ReplaceMemberLocationsRequest;
import vn.covie.merchants.application.MerchantMembershipService;
import vn.covie.shared.concurrency.VersionPrecondition;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/members")
public class MerchantMemberController {
    private final MerchantMembershipService members;

    public MerchantMemberController(MerchantMembershipService members) { this.members = members; }

    @GetMapping
    public List<MerchantMemberResponse> active(@PathVariable UUID merchantId) { return members.active(merchantId); }

    @PostMapping
    public ResponseEntity<MerchantMemberResponse> add(@PathVariable UUID merchantId,
            @Valid @RequestBody AddMerchantMemberRequest request) {
        MerchantMemberResponse member = members.add(merchantId, request);
        return ResponseEntity.status(201).eTag(VersionPrecondition.format(member.version())).body(member);
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<MerchantMemberResponse> update(@PathVariable UUID merchantId, @PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateMerchantMemberRequest request) {
        MerchantMemberResponse member = members.update(merchantId, memberId, ifMatch, request);
        return ResponseEntity.ok().eTag(VersionPrecondition.format(member.version())).body(member);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID merchantId, @PathVariable UUID memberId) {
        members.revoke(merchantId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{memberId}/locations")
    public ResponseEntity<Void> replaceLocations(@PathVariable UUID merchantId, @PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReplaceMemberLocationsRequest request) {
        long version = members.replaceLocations(merchantId, memberId, ifMatch, request);
        return ResponseEntity.noContent().eTag(VersionPrecondition.format(version)).build();
    }
}
