package vn.covie.reputation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.reputation.model.BuyerReputationOutcome;
import vn.covie.reputation.model.MerchantReputationOutcome;
import vn.covie.reputation.model.ReputationOrderOutcomeEntity;
import vn.covie.reputation.persistence.ReputationOrderOutcomeRepository;
import vn.covie.shared.time.AppClock;

@Service
public class ReputationProjectionService {
    private final OrderRepository orders; private final ReputationOrderOutcomeRepository outcomes; private final ReputationOutcomeClassifier classifier; private final ReputationStatsLockService stats; private final AppClock clock;
    public ReputationProjectionService(OrderRepository orders, ReputationOrderOutcomeRepository outcomes, ReputationOutcomeClassifier classifier, ReputationStatsLockService stats, AppClock clock) { this.orders=orders; this.outcomes=outcomes; this.classifier=classifier; this.stats=stats; this.clock=clock; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void projectOrder(UUID orderId, UUID sourceOutboxEventId) {
        project(orders.lockById(orderId).orElseThrow(), sourceOutboxEventId);
    }
    void projectCurrentOrderWithoutOutbox(vn.covie.orders.model.OrderEntity order) {
        project(order, null);
    }
    private void project(vn.covie.orders.model.OrderEntity order, UUID sourceOutboxEventId) {
        Instant now=clock.now(); ReputationContribution next=classifier.classify(order.getStatus());
        outcomes.findById(order.getId()).orElseGet(() -> outcomes.saveAndFlush(ReputationOrderOutcomeEntity.create(order.getId(), order.getBuyerUserId(), order.getMerchantProfileId(), BuyerReputationOutcome.NONE, MerchantReputationOutcome.NONE, order.getStatus().name(), sourceOutboxEventId, now)));
        ReputationOrderOutcomeEntity outcome=outcomes.findForUpdate(order.getId()).orElseThrow();
        if (outcome.getBuyerOutcome()==next.buyer() && outcome.getMerchantOutcome()==next.merchant()) { outcome.replace(next.buyer(), next.merchant(), order.getStatus().name(), sourceOutboxEventId, now); return; }
        var buyer=stats.user(order.getBuyerUserId(),now); var merchant=stats.merchant(order.getMerchantProfileId(),now);
        apply(buyer, outcome.getBuyerOutcome(), -1, now); apply(merchant, outcome.getMerchantOutcome(), -1, now);
        apply(buyer, next.buyer(), 1, now); apply(merchant, next.merchant(), 1, now);
        outcome.replace(next.buyer(), next.merchant(), order.getStatus().name(), sourceOutboxEventId, now);
    }
    static void apply(vn.covie.reputation.model.UserReputationStatsEntity stats, BuyerReputationOutcome outcome, int sign, Instant now) { switch(outcome) { case NONE -> {} case SUCCESS -> stats.adjust(sign,sign,0,0,0,now); case NEGATIVE_NO_SHOW -> stats.adjust(sign,0,sign,0,0,now); case NEGATIVE_CANCEL_AFTER_CONFIRM -> stats.adjust(sign,0,0,sign,0,now); case EXCLUDED -> stats.adjust(0,0,0,0,sign,now); } }
    static void apply(vn.covie.reputation.model.MerchantReputationStatsEntity stats, MerchantReputationOutcome outcome, int sign, Instant now) { switch(outcome) { case NONE -> {} case SUCCESS -> stats.adjust(sign,sign,0,0,now); case NEGATIVE_SELLER_CANCEL -> stats.adjust(sign,0,sign,0,now); case EXCLUDED -> stats.adjust(0,0,0,sign,now); } }
}
