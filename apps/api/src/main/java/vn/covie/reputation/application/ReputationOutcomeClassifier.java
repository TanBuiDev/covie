package vn.covie.reputation.application;

import vn.covie.orders.model.OrderStatus;
import vn.covie.reputation.model.BuyerReputationOutcome;
import vn.covie.reputation.model.MerchantReputationOutcome;

public class ReputationOutcomeClassifier {
    public ReputationContribution classify(OrderStatus status) {
        return switch (status) {
            case BUYER_NO_SHOW -> new ReputationContribution(BuyerReputationOutcome.NEGATIVE_NO_SHOW, MerchantReputationOutcome.NONE);
            case NO_SHOW_CONTESTED, CLOSED_WITH_ISSUE -> new ReputationContribution(BuyerReputationOutcome.EXCLUDED, MerchantReputationOutcome.EXCLUDED);
            case COMPLETED -> new ReputationContribution(BuyerReputationOutcome.SUCCESS, MerchantReputationOutcome.SUCCESS);
            case CANCELLED_BY_BUYER_AFTER_CONFIRMATION -> new ReputationContribution(BuyerReputationOutcome.NEGATIVE_CANCEL_AFTER_CONFIRM, MerchantReputationOutcome.NONE);
            case CANCELLED_BY_SELLER_AFTER_CONFIRMATION -> new ReputationContribution(BuyerReputationOutcome.NONE, MerchantReputationOutcome.NEGATIVE_SELLER_CANCEL);
            case PENDING_CONFIRMATION, CONFIRMED, OUT_FOR_DELIVERY, READY_FOR_PICKUP, AWAITING_BUYER_CONFIRMATION,
                    CANCELLATION_REQUESTED, DELIVERY_ISSUE_REPORTED, SERVICE_ISSUE_REPORTED, EXPIRED,
                    REJECTED_BY_SELLER, CANCELLED_BEFORE_CONFIRMATION -> new ReputationContribution(BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE);
        };
    }
}
