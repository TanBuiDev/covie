package vn.covie.merchants.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.merchants.api.dto.CreateMerchantRequest;
import vn.covie.merchants.api.dto.MerchantSummaryResponse;
import vn.covie.merchants.application.MerchantApplicationService;

@RestController
@RequestMapping("/api/v1/me/merchants")
public class MeMerchantController {
    private final MerchantApplicationService merchants;

    public MeMerchantController(MerchantApplicationService merchants) { this.merchants = merchants; }

    @GetMapping
    public List<MerchantSummaryResponse> mine() { return merchants.mine(); }

    @PostMapping
    public ResponseEntity<MerchantSummaryResponse> create(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchants.create(request));
    }
}
