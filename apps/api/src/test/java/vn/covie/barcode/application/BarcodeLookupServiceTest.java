package vn.covie.barcode.application;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.covie.barcode.model.BarcodeReferenceProductEntity;
import vn.covie.barcode.persistence.BarcodeReferenceProductRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarcodeLookupServiceTest {

    @Mock
    private BarcodeReferenceProductRepository repository;

    private BarcodeLookupService lookupService;

    @BeforeEach
    void setUp() {
        lookupService = new BarcodeLookupService(repository);
    }

    @Test
    void rejectsInvalidGtinChecksum() {
        assertThatThrownBy(() -> lookupService.lookup("8934673501355"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(apiEx.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });
    }

    @Test
    void throwsNotFoundWhenBarcodeNotInDatabase() {
        when(repository.findFirstByGtin14AndActiveTrue("08934673501354"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> lookupService.lookup("8934673501354"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                assertThat(apiEx.status()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void returnsMetadataWhenBarcodeFound() {
        BarcodeReferenceProductEntity entity = BarcodeReferenceProductEntity.of(
            1L,
            "8934673501354",
            "08934673501354",
            "VINAMILK Sữa tươi",
            "Vinamilk",
            "220ml",
            "Milks",
            "VN",
            "OFF_MARKET_TAG",
            "OPEN_FOOD_FACTS",
            "https://world.openfoodfacts.org/product/8934673501354",
            "https://world.openfoodfacts.org/product/8934673501354",
            "ODbL-1.0",
            "COMMUNITY_UNVERIFIED",
            true
        );

        when(repository.findFirstByGtin14AndActiveTrue("08934673501354"))
            .thenReturn(Optional.of(entity));

        var response = lookupService.lookup("8934673501354");

        assertThat(response.gtin()).isEqualTo("8934673501354");
        assertThat(response.productName()).isEqualTo("VINAMILK Sữa tươi");
        assertThat(response.brand()).isEqualTo("Vinamilk");
        assertThat(response.needsReview()).isTrue();
        assertThat(response.attributionNotice()).contains("Open Food Facts");
    }
}
