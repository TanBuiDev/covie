package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

@AutoConfigureMockMvc
class AdminMaintenanceAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired vn.covie.merchants.persistence.MerchantProfileRepository merchants;
    @Autowired vn.covie.merchants.persistence.MerchantLocationRepository locations;
    @Autowired vn.covie.orders.persistence.OrderRepository orders;
    private static final String PATH="/api/v1/admin/maintenance/reputation/rebuild";
    private String adminBearer() {
        var admin=activeUser("Maintenance operator");
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),admin.getId(),SystemRole.ADMIN,Instant.now()));
        return "Bearer "+issuer.issue(admin.getId(),device()).accessToken().value();
    }
    @ParameterizedTest @ValueSource(ints={0,501})
    void invalidReasonIsAClientErrorWithoutMaintenanceAudit(int length) throws Exception {
        mvc.perform(post(PATH).header("Authorization",adminBearer()).header("Idempotency-Key",UUID.randomUUID().toString())
                .header("X-Admin-Reason","x".repeat(length)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rebuildAll\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertNoMaintenanceAudit();
    }
    @Test void missingReasonIsAClientError() throws Exception {
        mvc.perform(post(PATH).header("Authorization",adminBearer()).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rebuildAll\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertNoMaintenanceAudit();
    }
    @Test void fullRebuildObeysDisabledPropertyAndAdminAccess() throws Exception {
        String ordinary="Bearer "+issuer.issue(activeUser("Buyer").getId(),device()).accessToken().value();
        mvc.perform(post(PATH).header("Authorization",ordinary).header("Idempotency-Key",UUID.randomUUID().toString())
                .header("X-Admin-Reason","Verify projections").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rebuildAll\":true}")).andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("Authorization",adminBearer()).header("Idempotency-Key",UUID.randomUUID().toString())
                .header("X-Admin-Reason","Verify projections").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rebuildAll\":true}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MAINTENANCE_REBUILD_DISABLED"));
        assertNoMaintenanceAudit();
    }
    private void assertNoMaintenanceAudit() {
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action in ('ADMIN_RATING_REBUILD','ADMIN_REPUTATION_REBUILD')",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"ratings","reputation"})
    void missingIdempotencyKeyUsesTheExistingClientErrorContract(String command) throws Exception {
        String body=command.equals("ratings") ? "{\"merchantId\":\""+UUID.randomUUID()+"\"}" : "{\"rebuildAll\":true}";
        mvc.perform(post("/api/v1/admin/maintenance/"+command+"/rebuild")
                .header("Authorization",adminBearer()).header("X-Admin-Reason","Verify projections")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertNoMaintenanceAudit();
    }
    @ParameterizedTest @ValueSource(strings={"merchantId","catalogItemId","orderId"})
    void nonexistentTargetsReturnNotFoundWithoutProjectionsOrAudit(String field) throws Exception {
        UUID id=UUID.randomUUID();
        String command=field.equals("orderId") ? "reputation" : "ratings";
        mvc.perform(post("/api/v1/admin/maintenance/"+command+"/rebuild")
                .header("Authorization",adminBearer()).header("Idempotency-Key",UUID.randomUUID().toString())
                .header("X-Admin-Reason","Verify target exists").contentType(MediaType.APPLICATION_JSON)
                .content("{\""+field+"\":\""+id+"\""+(field.equals("orderId") ? ",\"rebuildAll\":false" : "")+"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertNoMaintenanceAudit();
        assertThat(jdbc.queryForObject("select count(*) from merchant_rating_stats where merchant_profile_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from catalog_item_rating_stats where catalog_item_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?",Long.class,id)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED","BUYER_NO_SHOW"})
    void targetedReputationRebuildProjectsOnlySelectedOrderAndReplaysOnce(String status) throws Exception {
        String bearer=adminBearer();
        UUID actor=jdbc.queryForObject("select user_id from system_role_assignments where role='ADMIN'",UUID.class);
        var selected=orderFixture(status);
        var unrelated=orderFixture("COMPLETED");
        String key=UUID.randomUUID().toString();
        String body="{\"orderId\":\""+selected.id()+"\",\"rebuildAll\":false}";
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post(PATH).header("Authorization",bearer).header("Idempotency-Key",key)
                    .header("X-Admin-Reason","Restore selected order projection").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                    .andExpect(jsonPath("$.operation").value("REPUTATION_REBUILD"))
                    .andExpect(jsonPath("$.target").value("order:"+selected.id()));
        }
        mvc.perform(post(PATH).header("Authorization",bearer).header("Idempotency-Key",key)
                .header("X-Admin-Reason","Restore selected order projection").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\""+unrelated.id()+"\",\"rebuildAll\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST"));
        boolean completed=status.equals("COMPLETED");
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count from user_reputation_stats where user_id=?",selected.buyer()))
                .containsEntry("eligible_order_count",1L).containsEntry("completed_order_count",completed?1L:0L);
        assertThat(jdbc.queryForMap("select eligible_order_count,completed_order_count from merchant_reputation_stats where merchant_profile_id=?",selected.merchant()))
                .containsEntry("eligible_order_count",completed?1L:0L).containsEntry("completed_order_count",completed?1L:0L);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?",Long.class,selected.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reputation_order_outcomes where order_id=?",Long.class,unrelated.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_reputation_stats where user_id=?",Long.class,unrelated.buyer())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from merchant_reputation_stats where merchant_profile_id=?",Long.class,unrelated.merchant())).isZero();
        var auditRows=jdbc.queryForList("select actor_user_id,entity_type,after_snapshot->>'target' as target,after_snapshot->>'reason' as reason from audit_logs where action='ADMIN_REPUTATION_REBUILD'");
        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.getFirst()).containsEntry("actor_user_id",actor).containsEntry("entity_type","MAINTENANCE")
                .containsEntry("target","order:"+selected.id()).containsEntry("reason","Restore selected order projection");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,selected.id())).isZero();
        assertThat(jdbc.queryForObject("select status from orders where id=?",String.class,selected.id())).isEqualTo(status);
    }
    private OrderFixture orderFixture(String status) {
        Instant now=Instant.now();
        UUID buyer=activeUser("Maintenance buyer").getId();
        UUID merchant=UUID.randomUUID();
        merchants.saveAndFlush(vn.covie.merchants.model.MerchantProfileEntity.create(merchant,"Maintenance shop","maintenance-"+merchant,now));
        UUID location=UUID.randomUUID();
        locations.saveAndFlush(vn.covie.merchants.model.MerchantLocationEntity.create(location,merchant,"Main","1 Street",10.8,106.7,now));
        var order=vn.covie.orders.model.OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().replace("-","").substring(0,20),
                vn.covie.orders.model.OrderType.PRODUCT,buyer,merchant,location,vn.covie.shared.commerce.PaymentMethod.COD,100,
                vn.covie.orders.model.OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(60),now);
        if(status.equals("COMPLETED"))order.complete(now);else order.transitionTo(vn.covie.orders.model.OrderStatus.BUYER_NO_SHOW,now);
        orders.saveAndFlush(order);
        return new OrderFixture(order.getId(),buyer,merchant);
    }
    private record OrderFixture(UUID id,UUID buyer,UUID merchant) {}
}
