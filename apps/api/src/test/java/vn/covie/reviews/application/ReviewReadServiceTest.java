package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.reviews.persistence.CatalogItemRatingStatsRepository;
import vn.covie.reviews.persistence.MerchantRatingStatsRepository;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.persistence.UserRepository;

class ReviewReadServiceTest {
    @Test
    void rejectsLimitsOutsideThePublicCursorContract() {
        var service=new ReviewReadService(mock(ReviewRepository.class),mock(UserRepository.class),mock(MediaAssetRepository.class),
                mock(MediaUrlResolver.class),mock(CatalogItemRatingStatsRepository.class),mock(MerchantRatingStatsRepository.class),new ObjectMapper());

        assertThatThrownBy(() -> service.catalog(java.util.UUID.randomUUID(),0,null))
                .isInstanceOfSatisfying(ApiException.class,error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.merchant(java.util.UUID.randomUUID(),51,null))
                .isInstanceOfSatisfying(ApiException.class,error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
