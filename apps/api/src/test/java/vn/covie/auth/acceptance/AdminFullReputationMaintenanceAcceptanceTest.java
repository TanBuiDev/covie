package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.orders.model.*;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.reputation.allow-maintenance-rebuild=true","covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class AdminFullReputationMaintenanceAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired vn.covie.merchants.persistence.MerchantProfileRepository merchants;
    @Autowired vn.covie.merchants.persistence.MerchantLocationRepository locations;
    @Autowired vn.covie.orders.persistence.OrderRepository orders;
    @Autowired vn.covie.reputation.application.ReputationRebuildService rebuild;

    @Test void fullRebuildRepairsCorruptionAcrossPagesAndPersistsCompletedReplay() throws Exception {
        Instant now=Instant.now();
        var admin=activeUser("Full maintenance operator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
        UUID buyer=activeUser("Full rebuild buyer").getId(),merchant=UUID.randomUUID(),location=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Full maintenance shop","full-maintenance-"+merchant,now));
        locations.saveAndFlush(MerchantLocationEntity.create(location,merchant,"Main","1 Street",10.8,106.7,now));
        var fixtures=new ArrayList<OrderEntity>();
        for(int index=0;index<502;index++) {
            var order=OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().replace("-","").substring(0,20),
                    OrderType.PRODUCT,buyer,merchant,location,PaymentMethod.COD,100,OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(60),now);
            if(index<501)order.complete(now);else order.transitionTo(OrderStatus.BUYER_NO_SHOW,now);
            fixtures.add(order);
        }
        orders.saveAllAndFlush(fixtures);
        rebuild.rebuildOrder(fixtures.getFirst().getId());
        jdbc.update("update user_reputation_stats set eligible_order_count=99,completed_order_count=99 where user_id=?",buyer);
        jdbc.update("update merchant_reputation_stats set eligible_order_count=99,completed_order_count=99 where merchant_profile_id=?",merchant);
        String key=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post("/api/v1/admin/maintenance/reputation/rebuild").header("Authorization",bearer)
                    .header("Idempotency-Key",key).header("X-Admin-Reason","Repair all reputation projections")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rebuildAll\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.operation").value("REPUTATION_REBUILD"))
                    .andExpect(jsonPath("$.target").value("all"));
        }
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count from user_reputation_stats where user_id=?",buyer))
                .containsEntry("eligible_order_count",502L).containsEntry("completed_order_count",501L);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count from merchant_reputation_stats where merchant_profile_id=?",merchant))
                .containsEntry("eligible_order_count",501L).containsEntry("completed_order_count",501L);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where buyer_user_id=?",Long.class,buyer)).isEqualTo(502);
        assertThat(jdbc.queryForObject("select status from idempotency_records where actor_id=? and operation='admin.maintenance.reputation'",String.class,admin.getId())).isEqualTo("COMPLETED");
        var audits=jdbc.queryForList("select after_snapshot->>'target' as target,after_snapshot->>'reason' as reason from audit_logs where actor_user_id=? and action='ADMIN_REPUTATION_REBUILD'",admin.getId());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst()).containsEntry("target","all").containsEntry("reason","Repair all reputation projections");
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=? and status='COMPLETED'",Long.class,buyer)).isEqualTo(501);
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=? and status='BUYER_NO_SHOW'",Long.class,buyer)).isEqualTo(1);
    }
}
