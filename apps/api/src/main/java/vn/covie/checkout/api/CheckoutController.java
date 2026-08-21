package vn.covie.checkout.api;

import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.checkout.api.dto.CheckoutCommitRequest;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.api.dto.CheckoutPreviewResponse;
import vn.covie.checkout.api.dto.OrderCreatedResponse;
import vn.covie.checkout.application.CheckoutCommitService;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.shared.idempotency.IdempotencyExecutor;

@RestController
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {
    private final CheckoutPreviewService previews;
    private final CurrentActorProvider actors;
    private final IdempotencyExecutor idempotency;
    private final CheckoutCommitService commits;

    public CheckoutController(CheckoutPreviewService previews) {
        this(previews, null, null, null);
    }

    @Autowired
    public CheckoutController(CheckoutPreviewService previews, CurrentActorProvider actors,
            IdempotencyExecutor idempotency, CheckoutCommitService commits) {
        this.previews = previews;
        this.actors = actors;
        this.idempotency = idempotency;
        this.commits = commits;
    }

    @PostMapping("/preview")
    public ResponseEntity<CheckoutPreviewResponse> preview(@RequestBody CheckoutPreviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(previews.preview(request));
    }

    @PostMapping("/commit")
    public ResponseEntity<OrderCreatedResponse> commit(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CheckoutCommitRequest request) {
        var actor = actors.currentActor();
        OrderCreatedResponse result = idempotency.execute(actor.userId(), "CHECKOUT_COMMIT", key, request, 201,
                OrderCreatedResponse.class, () -> commits.commit(actor.userId(), request.checkoutToken()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.id())).body(result);
    }
}
