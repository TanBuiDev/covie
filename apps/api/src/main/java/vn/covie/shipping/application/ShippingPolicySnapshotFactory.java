package vn.covie.shipping.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.shipping.model.ShippingActionType;
import vn.covie.shipping.model.ShippingConditionOperator;
import vn.covie.shipping.model.ShippingConditionType;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.shipping.model.ShippingPolicyEntity;
import vn.covie.shipping.model.ShippingPolicyStatus;
import vn.covie.shipping.model.ShippingRuleEntity;
import vn.covie.shipping.model.ShippingRuleMatchMode;

@Component
public class ShippingPolicySnapshotFactory {
    private static final int SCHEMA_VERSION = 1;
    private final ObjectMapper mapper;

    public ShippingPolicySnapshotFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode snapshot(ShippingPolicyEntity policy, List<ShippingRuleEntity> rules,
            ShippingEvaluation evaluation) {
        List<RuleSnapshot> ruleSnapshots = rules.stream().map(rule -> new RuleSnapshot(rule.getId(),
                rule.getConditionType(), rule.getOperator(), rule.getThresholdValue(), rule.getActionType(),
                rule.getPriority(), rule.isEnabled())).toList();
        return mapper.valueToTree(new PolicySnapshot(SCHEMA_VERSION, policy.getId(), policy.getVersion(),
                policy.getPolicyStatus(), policy.getMode(), policy.getBaseFee(), policy.getCurrencyCode(),
                policy.getRuleMatchMode(), ruleSnapshots, evaluation.subtotalMinor(), evaluation.distanceMeters(),
                evaluation.matchedRuleIds(), evaluation.shippingFeeMinor()));
    }

    record PolicySnapshot(int schemaVersion, UUID policyId, long sourceVersion, ShippingPolicyStatus policyStatus,
            ShippingMode mode, Long baseFee, String currency, ShippingRuleMatchMode ruleMatchMode,
            List<RuleSnapshot> rules, long subtotalUsed, long distanceMetersUsed, List<UUID> matchedRuleIds,
            Long effectiveShippingFee) {}

    record RuleSnapshot(UUID id, ShippingConditionType conditionType, ShippingConditionOperator operator,
            long thresholdValue, ShippingActionType actionType, int priority, boolean enabled) {}
}
