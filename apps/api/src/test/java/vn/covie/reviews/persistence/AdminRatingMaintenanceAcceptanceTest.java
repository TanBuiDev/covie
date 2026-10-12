package vn.covie.reviews.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.shared.security.SystemRole;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;

@AutoConfigureMockMvc
class AdminRatingMaintenanceAcceptanceTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewRepository reviews;
    @Autowired InitialSessionIssuer issuer;
    @Autowired SystemRoleAssignmentRepository roles;

    @ParameterizedTest @ValueSource(strings={"catalogItemId","merchantId"})
    void rebuildRepairsOnlyRequestedAggregateExcludesHiddenReviewAndReplaysOnce(String field) throws Exception {
        var selected=ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        var unrelated=ReviewRepositoryIntegrationTest.Fixture.create(jdbc);
        Instant now=Instant.now();
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),selected.buyerId(),SystemRole.ADMIN,now));
        String bearer="Bearer "+issuer.issue(selected.buyerId(),new DeviceMetadata("Maintenance browser","maintenance-test")).accessToken().value();
        reviews.saveAndFlush(selected.review(selected.orderItemId(),4,now));
        var hidden=selected.review(selected.anotherOrderItemId(),5,now);
        hidden.hide(now); reviews.saveAndFlush(hidden);
        reviews.saveAndFlush(unrelated.review(unrelated.orderItemId(),2,now));
        seedStats(selected,99); seedStats(unrelated,77);
        boolean catalog=field.equals("catalogItemId");
        UUID target=catalog?selected.catalogItemId():selected.merchantId();
        UUID other=catalog?unrelated.catalogItemId():unrelated.merchantId();
        String key=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post("/api/v1/admin/maintenance/ratings/rebuild").header("Authorization",bearer)
                    .header("Idempotency-Key",key).header("X-Admin-Reason","Repair selected rating aggregate")
                    .contentType(MediaType.APPLICATION_JSON).content("{\""+field+"\":\""+target+"\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                    .andExpect(jsonPath("$.operation").value("RATING_REBUILD"))
                    .andExpect(jsonPath("$.target").value((catalog?"catalog:":"merchant:")+target));
        }
        mvc.perform(post("/api/v1/admin/maintenance/ratings/rebuild").header("Authorization",bearer)
                .header("Idempotency-Key",key).header("X-Admin-Reason","Repair selected rating aggregate")
                .contentType(MediaType.APPLICATION_JSON).content("{\""+field+"\":\""+other+"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST"));
        String table=catalog?"catalog_item_rating_stats":"merchant_rating_stats";
        String column=catalog?"catalog_item_id":"merchant_profile_id";
        var aggregate=jdbc.queryForMap("select review_count,rating_sum,average_rating from "+table+" where "+column+"=?",target);
        assertThat(aggregate).containsEntry("review_count",1L).containsEntry("rating_sum",4L);
        assertThat((java.math.BigDecimal)aggregate.get("average_rating")).isEqualByComparingTo("4.00");
        assertThat(jdbc.queryForObject("select review_count from "+table+" where "+column+"=?",Long.class,other)).isEqualTo(77);
        String oppositeTable=catalog?"merchant_rating_stats":"catalog_item_rating_stats";
        String oppositeColumn=catalog?"merchant_profile_id":"catalog_item_id";
        assertThat(jdbc.queryForObject("select review_count from "+oppositeTable+" where "+oppositeColumn+"=?",Long.class,catalog?selected.merchantId():selected.catalogItemId())).isEqualTo(99);
        assertThat(reviews.findById(hidden.getId()).orElseThrow().getReviewStatus()).isEqualTo(vn.covie.reviews.model.ReviewStatus.HIDDEN);
        var audits=jdbc.queryForList("select entity_type,after_snapshot->>'target' as target,after_snapshot->>'reason' as reason from audit_logs where actor_user_id=? and action='ADMIN_RATING_REBUILD'",selected.buyerId());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst()).containsEntry("entity_type","MAINTENANCE")
                .containsEntry("target",(catalog?"catalog:":"merchant:")+target).containsEntry("reason","Repair selected rating aggregate");
    }
    private void seedStats(ReviewRepositoryIntegrationTest.Fixture fixture,int count) {
        jdbc.update("insert into catalog_item_rating_stats(catalog_item_id,review_count,rating_sum,average_rating,updated_at) values (?,?,?,1,current_timestamp)",fixture.catalogItemId(),count,count);
        jdbc.update("insert into merchant_rating_stats(merchant_profile_id,review_count,rating_sum,average_rating,updated_at) values (?,?,?,1,current_timestamp)",fixture.merchantId(),count,count);
    }
}
