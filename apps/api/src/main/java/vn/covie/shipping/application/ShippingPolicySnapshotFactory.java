package vn.covie.shipping.application;
import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper; import java.util.*; import org.springframework.stereotype.Component; import vn.covie.shipping.model.*;
@Component public class ShippingPolicySnapshotFactory {
 private final ObjectMapper mapper; public ShippingPolicySnapshotFactory(ObjectMapper mapper){this.mapper=mapper;}
 public JsonNode snapshot(ShippingPolicyEntity policy,List<ShippingRuleEntity> rules,ShippingEvaluation evaluation){
  Map<String,Object> value=new LinkedHashMap<>(); value.put("mode",policy.getMode().name()); value.put("baseFee",policy.getBaseFee()); value.put("currency",policy.getCurrencyCode()); value.put("ruleMatchMode",policy.getRuleMatchMode()==null?null:policy.getRuleMatchMode().name()); value.put("rules",rules.stream().map(r->Map.of("id",r.getId(),"conditionType",r.getConditionType().name(),"operator",r.getOperator().name(),"thresholdValue",r.getThresholdValue(),"actionType",r.getActionType().name(),"priority",r.getPriority(),"enabled",r.isEnabled())).toList()); value.put("subtotalUsed",evaluation.subtotalMinor()); value.put("distanceMetersUsed",evaluation.distanceMeters()); value.put("matchedRuleIds",evaluation.matchedRuleIds()); value.put("effectiveShippingFee",evaluation.shippingFeeMinor()); return mapper.valueToTree(value);
 }
}
