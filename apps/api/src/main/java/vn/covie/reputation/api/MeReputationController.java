package vn.covie.reputation.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.reputation.api.dto.BuyerReputationSummary;
import vn.covie.reputation.application.ReputationReadService;

@RestController
@RequestMapping("/api/v1/me/reputation")
public class MeReputationController {
    private final CurrentActorProvider actors;
    private final ReputationReadService reputation;

    public MeReputationController(CurrentActorProvider actors, ReputationReadService reputation) {
        this.actors = actors;
        this.reputation = reputation;
    }

    @GetMapping
    public ResponseEntity<BuyerReputationSummary> get() {
        BuyerReputationSummary summary = reputation.buyer(actors.currentActor().userId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(summary);
    }
}
