package vn.covie.merchants.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantModerationVisibilityTest {
    @Test
    void hideAndRestoreDoNotChangeMerchantBusinessStatus() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        MerchantProfileEntity merchant = MerchantProfileEntity.create(UUID.randomUUID(), "Merchant", "merchant", now);

        merchant.hide(now.plusSeconds(1));
        assertThat(merchant.getMerchantStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchant.getModerationStatus()).isEqualTo(MerchantModerationStatus.HIDDEN);

        merchant.restoreVisibility(now.plusSeconds(2));
        assertThat(merchant.getMerchantStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchant.getModerationStatus()).isEqualTo(MerchantModerationStatus.APPROVED);
    }
}
