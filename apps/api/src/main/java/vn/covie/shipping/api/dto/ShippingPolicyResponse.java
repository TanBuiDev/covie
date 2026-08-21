package vn.covie.shipping.api.dto;
import java.util.List; import java.util.UUID; import vn.covie.shipping.model.*;
public record ShippingPolicyResponse(UUID id, UUID merchantLocationId, ShippingMode mode, Long baseFee, String currencyCode, ShippingRuleMatchMode ruleMatchMode, ShippingPolicyStatus status, long version, List<ShippingRuleRequest> rules) {}
