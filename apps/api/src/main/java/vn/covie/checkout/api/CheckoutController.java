package vn.covie.checkout.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.api.dto.CheckoutPreviewResponse;
import vn.covie.checkout.application.CheckoutPreviewService;

@RestController
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {
    private final CheckoutPreviewService previews;

    public CheckoutController(CheckoutPreviewService previews) {
        this.previews = previews;
    }

    @PostMapping("/preview")
    public ResponseEntity<CheckoutPreviewResponse> preview(@RequestBody CheckoutPreviewRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(previews.preview(request));
    }
}
