package vn.covie.merchants.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantSuspensionTest {
    @Test
    void restoresTheStatusThatExistedBeforeSuspension() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        MerchantProfileEntity merchant = MerchantProfileEntity.create(UUID.randomUUID(), "Merchant", "merchant", now);
        merchant.suspend(now.plusSeconds(1));
        assertThat(merchant.getMerchantStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        merchant.restore(now.plusSeconds(2));
        assertThat(merchant.getMerchantStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }
}
