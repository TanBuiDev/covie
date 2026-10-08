package vn.covie.ads.api;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import vn.covie.ads.AdService;
import vn.covie.shared.concurrency.VersionPrecondition;
@RestController @RequestMapping("/api/v1/admin/ads")
public class AdminAdController {
 private final AdService service;public AdminAdController(AdService s){service=s;}
 @GetMapping public List<AdCampaignResponse> list(@io.swagger.v3.oas.annotations.Parameter(description="Zero-based offset, 0 to 1000000. Returns at most 100 campaigns per page.") @RequestParam(defaultValue="0") int offset){return service.list(null,offset);}
 @PostMapping public ResponseEntity<AdCampaignResponse> create(@Valid @RequestBody AdCampaignRequest r){var result=service.create(null,r);return ResponseEntity.status(201).eTag(VersionPrecondition.format(result.version())).body(result);}
 @PatchMapping("/{id}") public ResponseEntity<AdCampaignResponse> update(@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match,@Valid @RequestBody AdCampaignRequest r){var result=service.update(null,id,match,r);return ResponseEntity.ok().eTag(VersionPrecondition.format(result.version())).body(result);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match){service.delete(null,id,match);return ResponseEntity.noContent().build();}
 @GetMapping("/{id}/stats") public AdStatsResponse stats(@PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.stats(null,id,from,to);}
 public record ReviewRequest(boolean approved,String reason){}
 @PostMapping("/{id}/review") public ResponseEntity<AdCampaignResponse> review(@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ReviewRequest r){var result=service.review(id,match,r.approved(),r.reason());return ResponseEntity.ok().eTag(VersionPrecondition.format(result.version())).body(result);}
}
