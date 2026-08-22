package vn.covie.reputation.application;

import vn.covie.reputation.model.BuyerReputationOutcome;
import vn.covie.reputation.model.MerchantReputationOutcome;

public record ReputationContribution(BuyerReputationOutcome buyer, MerchantReputationOutcome merchant) {}
