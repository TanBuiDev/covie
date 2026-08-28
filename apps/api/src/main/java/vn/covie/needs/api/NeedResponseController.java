package vn.covie.needs.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.needs.api.dto.CreateNeedResponseRequest;
import vn.covie.needs.api.dto.NeedResponseCandidateCursorPage;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.api.dto.ReplaceNeedResponseRequest;
import vn.covie.needs.application.NeedResponseCandidateService;
import vn.covie.needs.application.NeedResponseService;

@RestController
@RequestMapping("/api/v1/need-requests/{needRequestId}/responses")
public class NeedResponseController {
    private final NeedResponseService responses;
    private final NeedResponseCandidateService candidates;

    public NeedResponseController(NeedResponseService responses, NeedResponseCandidateService candidates) {
        this.responses = responses;
        this.candidates = candidates;
    }

    @GetMapping("/candidates")
    public NeedResponseCandidateCursorPage candidates(
            @PathVariable UUID needRequestId,
            @RequestParam UUID merchantId,
            @RequestParam UUID merchantLocationId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return candidates.list(needRequestId, merchantId, merchantLocationId, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<NeedResponseResponse> create(
            @PathVariable UUID needRequestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateNeedResponseRequest request) {
        var execution = responses.create(needRequestId, key, request);
        NeedResponseResponse body = execution.response();
        return ResponseEntity.created(URI.create("/api/v1/need-requests/" + needRequestId + "/responses/" + body.id()))
                .eTag("\"v" + body.version() + "\"")
                .body(body);
    }

    @PutMapping("/{responseId}")
    public ResponseEntity<NeedResponseResponse> replace(
            @PathVariable UUID needRequestId,
            @PathVariable UUID responseId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReplaceNeedResponseRequest request) {
        NeedResponseResponse body = responses.replace(needRequestId, responseId, ifMatch, request);
        return ResponseEntity.ok().eTag("\"v" + body.version() + "\"").body(body);
    }

    @PostMapping("/{responseId}/withdraw")
    public ResponseEntity<NeedResponseResponse> withdraw(
            @PathVariable UUID needRequestId,
            @PathVariable UUID responseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        NeedResponseResponse body = responses.withdraw(needRequestId, responseId, key).response();
        return ResponseEntity.ok().eTag("\"v" + body.version() + "\"").body(body);
    }
}
