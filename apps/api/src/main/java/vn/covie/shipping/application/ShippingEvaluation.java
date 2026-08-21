package vn.covie.shipping.application;
import com.fasterxml.jackson.databind.JsonNode; import java.util.List; import java.util.UUID; import vn.covie.shipping.model.ShippingMode;
public record ShippingEvaluation(ShippingMode mode, long subtotalMinor, Long shippingFeeMinor, Long totalMinor, long knownTotalMinor, String currency, long distanceMeters, List<UUID> matchedRuleIds, JsonNode policySnapshot) {}
