package vn.covie.needs.api;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.needs.api.dto.MerchantNeedCursorPage;
import vn.covie.needs.application.NeedMatchingService;
@RestController @RequestMapping("/api/v1/merchants/{merchantId}/need-requests")
public class MerchantNeedDiscoveryController {
    private final NeedMatchingService matching;
    public MerchantNeedDiscoveryController(NeedMatchingService matching){this.matching=matching;}
    @GetMapping public MerchantNeedCursorPage discover(@PathVariable UUID merchantId,@RequestParam(required=false) UUID locationId,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor){return matching.discover(merchantId,locationId,limit,cursor);}
}
