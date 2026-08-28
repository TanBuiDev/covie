package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class NeedResponseCandidateServiceTest {
    @Test
    void pagesOnlyItemsAcceptedByTheSubmitEligibilityPolicy() {
        UUID needId = UUID.randomUUID(), merchantId = UUID.randomUUID(), locationId = UUID.randomUUID();
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID staleId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        NeedRequestEntity need = mock(NeedRequestEntity.class);
        NeedRequestRepository needs = mock(NeedRequestRepository.class); when(needs.findById(needId)).thenReturn(Optional.of(need));
        LocationVariantEntity first = mock(LocationVariantEntity.class); when(first.getId()).thenReturn(firstId);
        LocationVariantEntity stale = mock(LocationVariantEntity.class); when(stale.getId()).thenReturn(staleId);
        LocationVariantRepository offers = mock(LocationVariantRepository.class);
        when(offers.findAllByMerchantLocationIdAndDeletedAtIsNull(locationId)).thenReturn(List.of(stale, first));
        NeedResponseEligibilityPolicy eligibility = mock(NeedResponseEligibilityPolicy.class);
        when(eligibility.validateCandidateScope(need, merchantId, locationId, Instant.EPOCH)).thenReturn(3);
        when(eligibility.validateCandidateItem(need, merchantId, locationId, firstId)).thenReturn(
                new EligibleNeedResponseItem(UUID.randomUUID(), UUID.randomUUID(), firstId, "Máy lọc không khí",
                        "Màu trắng", 1_000_000, "VND", JsonNodeFactory.instance.objectNode()));
        when(eligibility.validateCandidateItem(need, merchantId, locationId, staleId)).thenThrow(
                new ApiException(ErrorCode.NEED_RESPONSE_ITEM_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "stale"));
        NeedResponseCandidateService service = new NeedResponseCandidateService(needs, offers, eligibility, () -> Instant.EPOCH);

        var page = service.list(needId, merchantId, locationId, 20, null);

        assertThat(page.maxItems()).isEqualTo(3);
        assertThat(page.items()).singleElement().satisfies(candidate -> {
            assertThat(candidate.locationVariantId()).isEqualTo(firstId);
            assertThat(candidate.itemName()).isEqualTo("Máy lọc không khí");
            assertThat(candidate.variantName()).isEqualTo("Màu trắng");
            assertThat(candidate.available()).isTrue();
        });
        assertThat(page.hasMore()).isFalse();
    }
}
