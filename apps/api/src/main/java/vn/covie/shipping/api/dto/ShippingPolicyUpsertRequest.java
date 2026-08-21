package vn.covie.shipping.api.dto;
import java.util.List;
import vn.covie.shipping.model.*;
public record ShippingPolicyUpsertRequest(ShippingMode mode, Long baseFee, String currencyCode, ShippingRuleMatchMode ruleMatchMode, ShippingPolicyStatus status, List<ShippingRuleRequest> rules) {}
