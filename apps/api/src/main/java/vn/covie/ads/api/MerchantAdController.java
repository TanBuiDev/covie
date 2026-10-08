package vn.covie.ads.api;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import vn.covie.ads.AdService;
import vn.covie.shared.concurrency.VersionPrecondition;
@RestController @RequestMapping("/api/v1/merchants/{merchantId}/ads")
public class MerchantAdController {
 private final AdService service;public MerchantAdController(AdService s){service=s;}
 @GetMapping public List<AdCampaignResponse> list(@PathVariable UUID merchantId,@io.swagger.v3.oas.annotations.Parameter(description="Zero-based offset, 0 to 1000000. Returns at most 100 campaigns per page.") @RequestParam(defaultValue="0") int offset){return service.list(merchantId,offset);}
 @PostMapping public ResponseEntity<AdCampaignResponse> create(@PathVariable UUID merchantId,@Valid @RequestBody AdCampaignRequest r){var result=service.create(merchantId,r);return ResponseEntity.status(201).eTag(VersionPrecondition.format(result.version())).body(result);}
 @PatchMapping("/{id}") public ResponseEntity<AdCampaignResponse> update(@PathVariable UUID merchantId,@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match,@Valid @RequestBody AdCampaignRequest r){var result=service.update(merchantId,id,match,r);return ResponseEntity.ok().eTag(VersionPrecondition.format(result.version())).body(result);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID merchantId,@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match){service.delete(merchantId,id,match);return ResponseEntity.noContent().build();}
 @GetMapping("/{id}/stats") public AdStatsResponse stats(@PathVariable UUID merchantId,@PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.stats(merchantId,id,from,to);}
 @PostMapping("/{id}/submit") public ResponseEntity<AdCampaignResponse> submit(@PathVariable UUID merchantId,@PathVariable UUID id,@RequestHeader(value="If-Match",required=false)String match){var result=service.submit(merchantId,id,match);return ResponseEntity.ok().eTag(VersionPrecondition.format(result.version())).body(result);}
}
