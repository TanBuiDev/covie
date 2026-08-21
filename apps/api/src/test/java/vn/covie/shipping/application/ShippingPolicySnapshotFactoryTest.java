package vn.covie.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyEntity;
import vn.covie.shipping.model.ShippingPolicyStatus;

class ShippingPolicySnapshotFactoryTest {
    @Test
    void preservesTheSubtotalInsteadOfTheTotalWithShipping() {
        ShippingPolicyEntity policy = ShippingPolicyEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), ShippingMode.FIXED, 20L, "VND", null,
                ShippingPolicyStatus.ACTIVE, Instant.parse("2026-08-22T00:00:00Z"));
        ShippingEvaluation evaluation = new ShippingEvaluator().evaluate(policy, List.of(), 100L, 0L);

        var snapshot = new ShippingPolicySnapshotFactory(new ObjectMapper()).snapshot(policy, List.of(), evaluation);

        assertThat(snapshot.path("subtotalUsed").asLong()).isEqualTo(100L);
        assertThat(snapshot.path("effectiveShippingFee").asLong()).isEqualTo(20L);
    }
}
