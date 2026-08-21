package vn.covie.shipping.application;
import java.util.*; import org.springframework.stereotype.Service; import vn.covie.shipping.model.*;
@Service public class ShippingEvaluator {
 public ShippingEvaluation evaluate(ShippingPolicyEntity policy,List<ShippingRuleEntity> rules,long subtotal,long distanceMeters){
  List<UUID> matched=new ArrayList<>(); long fee; Long total;
  switch(policy.getMode()){
   case FREE -> {fee=0;total=Math.addExact(subtotal,fee);}
   case FIXED -> {fee=Objects.requireNonNull(policy.getBaseFee());total=Math.addExact(subtotal,fee);}
   case NEGOTIATED -> {return new ShippingEvaluation(ShippingMode.NEGOTIATED,subtotal,null,null,subtotal,policy.getCurrencyCode(),distanceMeters,List.of(),null);}
   case CONDITIONAL -> {for(var r:rules)if(r.isEnabled()&&matches(r,subtotal,distanceMeters))matched.add(r.getId());boolean free=policy.getRuleMatchMode()==ShippingRuleMatchMode.ALL?rules.stream().filter(ShippingRuleEntity::isEnabled).count()==matched.size()&&!matched.isEmpty():!matched.isEmpty();fee=free?0:Objects.requireNonNull(policy.getBaseFee());total=Math.addExact(subtotal,fee);}
   default -> throw new IllegalStateException();
  }
  return new ShippingEvaluation(policy.getMode(),subtotal,fee,total,total,policy.getCurrencyCode(),distanceMeters,List.copyOf(matched),null);
 }
 private boolean matches(ShippingRuleEntity r,long subtotal,long distance){return switch(r.getConditionType()){case ORDER_AMOUNT_MIN->subtotal>=r.getThresholdValue();case DISTANCE_MAX->distance<=r.getThresholdValue();};}
}
