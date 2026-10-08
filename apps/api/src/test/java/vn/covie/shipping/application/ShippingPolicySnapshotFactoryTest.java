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
import vn.covie.shipping.model.ShippingRuleEntity;
import vn.covie.shipping.model.ShippingRuleMatchMode;
import vn.covie.shipping.model.ShippingConditionType;
import vn.covie.shipping.model.ShippingConditionOperator;
import vn.covie.shipping.model.ShippingActionType;

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
        assertThat(snapshot.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(snapshot.path("policyId").asText()).isEqualTo(policy.getId().toString());
        assertThat(snapshot.path("policyStatus").asText()).isEqualTo("ACTIVE");
        assertThat(snapshot.path("distanceMetersUsed").asLong()).isZero();
    }

    @Test
    void preservesNegotiatedNullFeeAndConditionalRuleOutcome() {
        ShippingPolicyEntity negotiated = ShippingPolicyEntity.create(UUID.randomUUID(), UUID.randomUUID(),
                ShippingMode.NEGOTIATED, null, "VND", null, ShippingPolicyStatus.ACTIVE, Instant.now());
        ShippingEvaluation negotiatedEvaluation = new ShippingEvaluator().evaluate(negotiated, List.of(), 100L, 900L);
        var negotiatedSnapshot = new ShippingPolicySnapshotFactory(new ObjectMapper())
                .snapshot(negotiated, List.of(), negotiatedEvaluation);
        assertThat(negotiatedSnapshot.path("effectiveShippingFee").isNull()).isTrue();
        assertThat(negotiatedSnapshot.path("distanceMetersUsed").asLong()).isEqualTo(900L);

        ShippingPolicyEntity conditional = ShippingPolicyEntity.create(UUID.randomUUID(), UUID.randomUUID(),
                ShippingMode.CONDITIONAL, 20L, "VND", ShippingRuleMatchMode.ANY,
                ShippingPolicyStatus.ACTIVE, Instant.now());
        ShippingRuleEntity rule = ShippingRuleEntity.create(UUID.randomUUID(), conditional.getId(),
                ShippingConditionType.DISTANCE_MAX, ShippingConditionOperator.LTE, 1_000L,
                ShippingActionType.FREE_SHIPPING, 1, true);
        ShippingEvaluation evaluation = new ShippingEvaluator().evaluate(conditional, List.of(rule), 100L, 900L);
        var snapshot = new ShippingPolicySnapshotFactory(new ObjectMapper()).snapshot(conditional, List.of(rule), evaluation);

        assertThat(snapshot.path("matchedRuleIds").get(0).asText()).isEqualTo(rule.getId().toString());
        assertThat(snapshot.path("rules").get(0).path("conditionType").asText()).isEqualTo("DISTANCE_MAX");
        assertThat(snapshot.path("rules").get(0).path("enabled").asBoolean()).isTrue();
        assertThat(snapshot.path("effectiveShippingFee").asLong()).isZero();
    }
}
