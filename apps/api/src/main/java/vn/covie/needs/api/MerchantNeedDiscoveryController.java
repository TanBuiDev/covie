package vn.covie.needs.api;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.needs.api.dto.MerchantNeedCursorPage;
import vn.covie.needs.api.dto.NeedEligibleItemCursorPage;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.needs.application.NeedEligibleItemReadService;
import vn.covie.needs.application.NeedMatchingService;
import vn.covie.needs.application.NeedResponseReadService;
@RestController @RequestMapping("/api/v1/merchants/{merchantId}/need-requests")
public class MerchantNeedDiscoveryController {
    private final NeedMatchingService matching;
    private final NeedEligibleItemReadService eligibleItems;
    private final NeedResponseReadService responses;
    public MerchantNeedDiscoveryController(NeedMatchingService matching, NeedEligibleItemReadService eligibleItems,
            NeedResponseReadService responses) {
        this.matching = matching;
        this.eligibleItems = eligibleItems;
        this.responses = responses;
    }
    @GetMapping public MerchantNeedCursorPage discover(@PathVariable UUID merchantId,@RequestParam(required=false) UUID locationId,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor){return matching.discover(merchantId,locationId,limit,cursor);}
    @GetMapping("/{needRequestId}/eligible-items") public NeedEligibleItemCursorPage eligibleItems(
            @PathVariable UUID merchantId, @PathVariable UUID needRequestId,
            @RequestParam UUID locationId, @RequestParam(required=false) Integer limit,
            @RequestParam(required=false) String cursor) {
        return eligibleItems.list(merchantId, needRequestId, locationId, limit, cursor);
    }

    @GetMapping("/{needRequestId}/response")
    public NeedResponseResponse response(@PathVariable UUID merchantId, @PathVariable UUID needRequestId,
            @RequestParam UUID locationId) {
        return responses.merchantResponse(merchantId, needRequestId, locationId);
    }
}
