package vn.covie.shipping.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "shipping_rules")
public class ShippingRuleEntity {
    @Id private UUID id;
    @Column(name = "shipping_policy_id", nullable = false) private UUID shippingPolicyId;
    @Enumerated(EnumType.STRING) @Column(name = "condition_type", nullable = false, length = 30) private ShippingConditionType conditionType;
    @Enumerated(EnumType.STRING) @Column(name = "operator", nullable = false, length = 10) private ShippingConditionOperator operator;
    @Column(name = "threshold_value", nullable = false) private long thresholdValue;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 30) private ShippingActionType actionType;
    @Column(nullable = false) private int priority;
    @Column(nullable = false) private boolean enabled;
    protected ShippingRuleEntity() {}
    public static ShippingRuleEntity create(UUID id, UUID policyId, ShippingConditionType type, ShippingConditionOperator op, long threshold, ShippingActionType action, int priority, boolean enabled) { ShippingRuleEntity r=new ShippingRuleEntity(); r.id=id;r.shippingPolicyId=policyId;r.conditionType=type;r.operator=op;r.thresholdValue=threshold;r.actionType=action;r.priority=priority;r.enabled=enabled;return r; }
    public UUID getId(){return id;} public UUID getShippingPolicyId(){return shippingPolicyId;} public ShippingConditionType getConditionType(){return conditionType;} public ShippingConditionOperator getOperator(){return operator;} public long getThresholdValue(){return thresholdValue;} public ShippingActionType getActionType(){return actionType;} public int getPriority(){return priority;} public boolean isEnabled(){return enabled;}
}
