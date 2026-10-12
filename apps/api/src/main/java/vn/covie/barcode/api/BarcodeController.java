package vn.covie.barcode.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.barcode.api.dto.BarcodeReferenceResponse;
import vn.covie.barcode.application.BarcodeLookupService;

@RestController
@RequestMapping("/api/v1/barcodes")
public class BarcodeController {

    private final BarcodeLookupService lookupService;

    public BarcodeController(BarcodeLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/{gtin}")
    public BarcodeReferenceResponse getByGtin(@PathVariable String gtin) {
        return lookupService.lookup(gtin);
    }
}
