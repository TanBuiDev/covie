package vn.covie.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.covie.orders.model.OrderStatus;
import vn.covie.reputation.model.BuyerReputationOutcome;
import vn.covie.reputation.model.MerchantReputationOutcome;

class ReputationOutcomeClassifierTest {
    private final ReputationOutcomeClassifier classifier = new ReputationOutcomeClassifier();

    @ParameterizedTest
    @MethodSource("cases")
    void classifiesEveryOrderStatus(OrderStatus status, BuyerReputationOutcome buyer, MerchantReputationOutcome merchant) {
        assertThat(classifier.classify(status)).isEqualTo(new ReputationContribution(buyer, merchant));
    }

    static Stream<Arguments> cases() {
        Set<OrderStatus> covered = Arrays.stream(OrderStatus.values()).collect(Collectors.toSet());
        assertThat(covered).hasSize(OrderStatus.values().length);
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_CONFIRMATION, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.CONFIRMED, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.OUT_FOR_DELIVERY, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.READY_FOR_PICKUP, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.AWAITING_BUYER_CONFIRMATION, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.CANCELLATION_REQUESTED, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.DELIVERY_ISSUE_REPORTED, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.SERVICE_ISSUE_REPORTED, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.BUYER_NO_SHOW, BuyerReputationOutcome.NEGATIVE_NO_SHOW, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.NO_SHOW_CONTESTED, BuyerReputationOutcome.EXCLUDED, MerchantReputationOutcome.EXCLUDED),
                Arguments.of(OrderStatus.COMPLETED, BuyerReputationOutcome.SUCCESS, MerchantReputationOutcome.SUCCESS),
                Arguments.of(OrderStatus.EXPIRED, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.REJECTED_BY_SELLER, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.CANCELLED_BEFORE_CONFIRMATION, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.CANCELLED_BY_BUYER_AFTER_CONFIRMATION, BuyerReputationOutcome.NEGATIVE_CANCEL_AFTER_CONFIRM, MerchantReputationOutcome.NONE),
                Arguments.of(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION, BuyerReputationOutcome.NONE, MerchantReputationOutcome.NEGATIVE_SELLER_CANCEL),
                Arguments.of(OrderStatus.CLOSED_WITH_ISSUE, BuyerReputationOutcome.EXCLUDED, MerchantReputationOutcome.EXCLUDED));
    }
}
