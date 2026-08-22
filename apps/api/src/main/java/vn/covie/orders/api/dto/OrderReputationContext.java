package vn.covie.orders.api.dto;

import vn.covie.reputation.api.dto.BuyerReputationSummary;
import vn.covie.reputation.api.dto.MerchantReputationSummary;

public record OrderReputationContext(MerchantReputationSummary merchant, BuyerReputationSummary buyer) {}
