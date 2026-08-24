package vn.covie.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.reputation.persistence.MerchantReputationStatsRepository;
import vn.covie.reputation.persistence.UserReputationStatsRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class ReputationProjectionLifecycleIntegrationTest extends PostgresIntegrationTest {
    @Autowired ReputationRebuildService rebuild;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantLocationRepository locations;
    @Autowired UserReputationStatsRepository userStats;
    @Autowired MerchantReputationStatsRepository merchantStats;
    @Autowired JdbcTemplate jdbc;

    @Test @Transactional
    void reversibleNoShowLifecycleReplacesItsContributionInsteadOfDoubleCounting() {
        Instant now=Instant.parse("2026-08-22T00:00:00Z"); UUID buyer=UUID.randomUUID(), merchant=UUID.randomUUID();
        users.save(UserEntity.active(buyer,"buyer-"+buyer,now)); merchants.save(MerchantProfileEntity.create(merchant,"Merchant","merchant-"+merchant,now));
        UUID location=locations.save(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 Street",10.8,106.7,now)).getId();
        var order=orders.saveAndFlush(OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().replace("-","").substring(0,20),OrderType.PRODUCT,buyer,merchant,location,PaymentMethod.COD,100,OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(60),now));

        order.transitionTo(OrderStatus.BUYER_NO_SHOW,now.plusSeconds(1)); rebuild.rebuildOrder(order.getId());
        assertThat(userStats.findById(buyer).orElseThrow().getEligibleOrderCount()).isEqualTo(1);
        assertThat(merchantStats.findById(merchant).orElseThrow().getEligibleOrderCount()).isZero();

        order.transitionTo(OrderStatus.NO_SHOW_CONTESTED,now.plusSeconds(2)); rebuild.rebuildOrder(order.getId());
        assertThat(userStats.findById(buyer).orElseThrow().getEligibleOrderCount()).isZero();
        assertThat(merchantStats.findById(merchant).orElseThrow().getEligibleOrderCount()).isZero();

        order.complete(now.plusSeconds(3)); rebuild.rebuildOrder(order.getId());
        assertThat(userStats.findById(buyer).orElseThrow().getEligibleOrderCount()).isEqualTo(1);
        assertThat(userStats.findById(buyer).orElseThrow().getCompletedOrderCount()).isEqualTo(1);
        assertThat(merchantStats.findById(merchant).orElseThrow().getEligibleOrderCount()).isEqualTo(1);
        assertThat(merchantStats.findById(merchant).orElseThrow().getCompletedOrderCount()).isEqualTo(1);
    }

    @Test @Transactional
    void completeOutcomeMatrixProducesTheRequiredBuyerAndMerchantTotals() {
        Instant now=Instant.parse("2026-08-23T00:00:00Z"); UUID buyer=UUID.randomUUID(), merchant=UUID.randomUUID();
        users.save(UserEntity.active(buyer,"buyer-"+buyer,now)); merchants.save(MerchantProfileEntity.create(merchant,"Merchant","merchant-"+merchant,now));
        UUID location=locations.save(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 Street",10.8,106.7,now)).getId();
        var a=createOrder(buyer,merchant,location,now); a.complete(now); orders.saveAndFlush(a); rebuild.rebuildOrder(a.getId());
        var b=createOrder(buyer,merchant,location,now.plusSeconds(1)); b.transitionTo(OrderStatus.BUYER_NO_SHOW,now.plusSeconds(1)); orders.saveAndFlush(b); rebuild.rebuildOrder(b.getId()); b.transitionTo(OrderStatus.NO_SHOW_CONTESTED,now.plusSeconds(2)); orders.saveAndFlush(b); rebuild.rebuildOrder(b.getId()); b.complete(now.plusSeconds(3)); orders.saveAndFlush(b); rebuild.rebuildOrder(b.getId());
        var c=createOrder(buyer,merchant,location,now.plusSeconds(4)); c.close(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION,now.plusSeconds(4)); orders.saveAndFlush(c); rebuild.rebuildOrder(c.getId());
        var d=createOrder(buyer,merchant,location,now.plusSeconds(5)); d.close(OrderStatus.CLOSED_WITH_ISSUE,now.plusSeconds(5)); orders.saveAndFlush(d); rebuild.rebuildOrder(d.getId());

        userStats.flush(); merchantStats.flush();

        var buyerRow=jdbc.queryForMap("select eligible_order_count,completed_order_count,excluded_issue_count,completion_rate_basis_points from user_reputation_stats where user_id=?",buyer);
        var merchantRow=jdbc.queryForMap("select eligible_order_count,completed_order_count,seller_cancel_after_confirm_count,excluded_issue_count,completion_rate_basis_points from merchant_reputation_stats where merchant_profile_id=?",merchant);
        assertThat(buyerRow).containsEntry("eligible_order_count",2L).containsEntry("completed_order_count",2L).containsEntry("excluded_issue_count",1L).containsEntry("completion_rate_basis_points",10000);
        assertThat(merchantRow).containsEntry("eligible_order_count",3L).containsEntry("completed_order_count",2L).containsEntry("seller_cancel_after_confirm_count",1L).containsEntry("excluded_issue_count",1L).containsEntry("completion_rate_basis_points",6666);
    }

    private OrderEntity createOrder(UUID buyer, UUID merchant, UUID location, Instant now) {
        return orders.saveAndFlush(OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().replace("-","").substring(0,20),OrderType.PRODUCT,buyer,merchant,location,PaymentMethod.COD,100,OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(60),now));
    }
}
