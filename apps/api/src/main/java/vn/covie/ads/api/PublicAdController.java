package vn.covie.ads.api;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import vn.covie.ads.AdService;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.ads.api.AdCampaignRequest.*;
@RestController @RequestMapping("/api/v1/ads")
@vn.covie.ratelimit.RateLimited(vn.covie.ratelimit.RateLimitGroup.PUBLIC_DISCOVERY)
public class PublicAdController {
 private final AdService service;public PublicAdController(AdService s){service=s;}
 @GetMapping("/active") public ResponseEntity<List<PublicAdResponse>> active(@RequestParam Placement placement,@RequestParam Device device){return ResponseEntity.ok().header("Cache-Control","no-store").body(service.active(placement,device));}
 @PostMapping("/{id}/events") public ResponseEntity<Void> event(@PathVariable UUID id,@Valid @RequestBody AdEventRequest event){service.event(id,event);return ResponseEntity.noContent().build();}
}
