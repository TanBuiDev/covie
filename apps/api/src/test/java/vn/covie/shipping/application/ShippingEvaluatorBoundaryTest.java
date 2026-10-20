package vn.covie.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.covie.shipping.model.ShippingActionType;
import vn.covie.shipping.model.ShippingConditionOperator;
import vn.covie.shipping.model.ShippingConditionType;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyEntity;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.model.ShippingRuleEntity;
import vn.covie.shipping.model.ShippingRuleMatchMode;

class ShippingEvaluatorBoundaryTest {
    @ParameterizedTest
    @CsvSource({
        "ANY, 99999, 1000, 0", "ANY, 100000, 1001, 0", "ANY, 99999, 1001, 5000",
        "ALL, 100000, 1000, 0", "ALL, 99999, 1000, 5000", "ALL, 100000, 1001, 5000"
    })
    void conditionalAnyAndAllRespectInclusiveAmountAndDistanceThresholds(
            ShippingRuleMatchMode matchMode, long subtotal, long distance, long expectedFee) {
        UUID policyId = UUID.randomUUID();
        ShippingPolicyEntity policy = ShippingPolicyEntity.create(policyId, UUID.randomUUID(),
                ShippingMode.CONDITIONAL, 5000L, "VND", matchMode, ShippingPolicyStatus.ACTIVE, Instant.EPOCH);
        ShippingRuleEntity amount = ShippingRuleEntity.create(UUID.randomUUID(), policyId,
                ShippingConditionType.ORDER_AMOUNT_MIN, ShippingConditionOperator.GTE, 100000,
                ShippingActionType.FREE_SHIPPING, 1, true);
        ShippingRuleEntity distanceRule = ShippingRuleEntity.create(UUID.randomUUID(), policyId,
                ShippingConditionType.DISTANCE_MAX, ShippingConditionOperator.LTE, 1000,
                ShippingActionType.FREE_SHIPPING, 2, true);
        ShippingEvaluation result = new ShippingEvaluator().evaluate(policy, List.of(amount, distanceRule), subtotal, distance);
        assertThat(result.shippingFeeMinor()).isEqualTo(expectedFee);
        assertThat(result.totalMinor()).isEqualTo(subtotal + expectedFee);
        if (subtotal >= 100000) assertThat(result.matchedRuleIds()).contains(amount.getId());
        else assertThat(result.matchedRuleIds()).doesNotContain(amount.getId());
        if (distance <= 1000) assertThat(result.matchedRuleIds()).contains(distanceRule.getId());
        else assertThat(result.matchedRuleIds()).doesNotContain(distanceRule.getId());
    }
}
