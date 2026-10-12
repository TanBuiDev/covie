package vn.covie.barcode.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.barcode.api.dto.BarcodeReferenceResponse;
import vn.covie.barcode.model.BarcodeReferenceProductEntity;
import vn.covie.barcode.persistence.BarcodeReferenceProductRepository;
import vn.covie.barcode.support.GtinValidator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class BarcodeLookupService {

    private final BarcodeReferenceProductRepository repository;

    public BarcodeLookupService(BarcodeReferenceProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BarcodeReferenceResponse lookup(String gtin) {
        if (!GtinValidator.isValidGtin(gtin)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Mã GTIN/Barcode không hợp lệ hoặc sai số kiểm tra.");
        }

        String gtin14 = GtinValidator.toGtin14(gtin);
        BarcodeReferenceProductEntity entity = repository.findFirstByGtin14AndActiveTrue(gtin14)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu tham chiếu cho mã vạch này."));

        boolean needsReview = "NEEDS_REVIEW".equalsIgnoreCase(entity.getQualityStatus())
            || "COMMUNITY_UNVERIFIED".equalsIgnoreCase(entity.getQualityStatus());

        String attributionNotice = "Dữ liệu tham khảo từ Open Food Facts (ODbL 1.0). Thông tin do cộng đồng đóng góp, người bán cần xác nhận lại.";

        return new BarcodeReferenceResponse(
            entity.getGtin(),
            entity.getGtin14(),
            entity.getProductName(),
            entity.getBrand(),
            entity.getQuantity(),
            entity.getCategoryLabel(),
            entity.getMarketCountry(),
            entity.getMarketSignal(),
            entity.getSource(),
            entity.getSourceRecordUrl(),
            entity.getLicenseId(),
            entity.getQualityStatus(),
            needsReview,
            attributionNotice
        );
    }
}
