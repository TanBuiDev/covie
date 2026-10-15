package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingTrialServiceTest {
    @Test void onlyExplicitAdminTrialPolicyCanEnableTrial() {
        assertFalse(BillingTrialService.trialEnabled(null));
        assertFalse(BillingTrialService.trialEnabled(new BillingCatalogService.Policy(
            UUID.randomUUID(), 1, "PAY_BEFORE_SELL", 30, 3, Instant.EPOCH)));
        assertFalse(BillingTrialService.trialEnabled(new BillingCatalogService.Policy(
            UUID.randomUUID(), 2, "TRIAL", 0, 3, Instant.EPOCH)));
        assertTrue(BillingTrialService.trialEnabled(new BillingCatalogService.Policy(
            UUID.randomUUID(), 3, "TRIAL", 30, 3, Instant.EPOCH)));
    }
}
