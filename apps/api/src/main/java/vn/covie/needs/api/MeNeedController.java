package vn.covie.needs.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.needs.api.dto.CreateNeedRequest;
import vn.covie.needs.api.dto.NeedCursorPage;
import vn.covie.needs.api.dto.NeedDetailResponse;
import vn.covie.needs.api.dto.NeedResponseCursorPage;
import vn.covie.needs.application.NeedCommandService;
import vn.covie.needs.application.NeedReadService;
import vn.covie.needs.application.NeedResponseReadService;
import vn.covie.needs.model.NeedStatus;

@RestController
@RequestMapping("/api/v1/me/need-requests")
public class MeNeedController {
    private final NeedCommandService commands;
    private final NeedReadService reads;
    private final NeedResponseReadService responseReads;

    public MeNeedController(NeedCommandService commands, NeedReadService reads, NeedResponseReadService responseReads) {
        this.commands = commands; this.reads = reads; this.responseReads = responseReads;
    }

    @GetMapping
    public NeedCursorPage list(@RequestParam(required = false) NeedStatus status,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return reads.list(status, limit, cursor);
    }

    @GetMapping("/{needRequestId}")
    public NeedDetailResponse detail(@PathVariable UUID needRequestId) { return reads.detail(needRequestId); }

    @GetMapping("/{needRequestId}/responses")
    public NeedResponseCursorPage responses(@PathVariable UUID needRequestId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return responseReads.list(needRequestId, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<NeedDetailResponse> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateNeedRequest request) {
        var execution = commands.create(key, request);
        return ResponseEntity.created(URI.create("/api/v1/me/need-requests/" + execution.response().id())).body(execution.response());
    }

    @PostMapping("/{needRequestId}/cancel")
    public ResponseEntity<NeedDetailResponse> cancel(@PathVariable UUID needRequestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.ok(commands.cancel(needRequestId, key).response());
    }
}
