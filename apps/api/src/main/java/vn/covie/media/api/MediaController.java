package vn.covie.media.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.media.api.dto.CreateMediaUploadRequest;
import vn.covie.media.api.dto.MediaResponse;
import vn.covie.media.api.dto.MediaUploadResponse;
import vn.covie.media.application.MediaUploadService;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaUploadService uploads;
    public MediaController(MediaUploadService uploads) { this.uploads = uploads; }
    @PostMapping("/uploads")
    public ResponseEntity<MediaUploadResponse> createUpload(@Valid @RequestBody CreateMediaUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploads.createUpload(request));
    }
    @PostMapping("/{mediaId}/complete")
    public MediaResponse complete(@PathVariable UUID mediaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return uploads.complete(mediaId, idempotencyKey);
    }
}
