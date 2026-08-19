package vn.covie.shipping.api.dto;
import vn.covie.shipping.model.*;
public record ShippingRuleRequest(ShippingConditionType conditionType, ShippingConditionOperator operator, long thresholdValue, ShippingActionType actionType, int priority, boolean enabled) {}
