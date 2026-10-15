package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingLegacyMigrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test void cutoffCannotTargetFutureMerchants() {
        assertThrows(RuntimeException.class,
            () -> BillingLegacyMigrationService.validateCutoff(NOW.plusSeconds(1), NOW));
    }

    @Test void cutoffMustBeExplicit() {
        assertThrows(RuntimeException.class,
            () -> BillingLegacyMigrationService.validateCutoff(null, NOW));
    }
}
