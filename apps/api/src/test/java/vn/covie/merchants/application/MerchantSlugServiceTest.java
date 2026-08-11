package vn.covie.merchants.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.merchants.persistence.MerchantProfileRepository;

class MerchantSlugServiceTest {

    @Test
    void refusesAnAlreadyAllocatedUuidSuffixedSlugSoCreationCanRetryWithANewId() {
        MerchantProfileRepository profiles = mock(MerchantProfileRepository.class);
        UUID merchantId = UUID.fromString("abcdef12-0000-7000-8000-000000000000");
        when(profiles.existsBySlugAndDeletedAtIsNull("tap-hoa-minh")).thenReturn(true);
        when(profiles.existsBySlugAndDeletedAtIsNull("tap-hoa-minh-abcdef")).thenReturn(true);
        MerchantSlugService slugs = new MerchantSlugService(profiles);

        assertThatThrownBy(() -> slugs.uniqueSlug("Tạp hóa Minh", merchantId))
                .isInstanceOf(MerchantSlugService.SlugCollisionException.class);
    }
}
