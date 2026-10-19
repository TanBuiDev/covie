package vn.covie.social.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.activity-enabled=true","covie.outbox.max-events-per-poll=0",
        "covie.retention.enabled=false"})
class CommentDeleteHideConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired MerchantProfileRepository merchants;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;

    @Test void authorDeleteAndModeratorHideHaveConsistentContendedOutcomes() throws Exception {
        var observed=new ArrayList<String>();
        for (boolean deleteFirst:List.of(true,false)) {
            Instant now=Instant.now();
            UUID author=UUID.randomUUID(),admin=UUID.randomUUID(),reporter=UUID.randomUUID();
            users.saveAllAndFlush(List.of(UserEntity.active(author,"Comment race author",now),
                    UserEntity.active(admin,"Comment race admin",now),UserEntity.active(reporter,"Comment race reporter",now)));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            UUID merchant=UUID.randomUUID(),category=UUID.randomUUID(),item=UUID.randomUUID();
            merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Comment race shop","comment-race-"+merchant,now));
            categories.saveAndFlush(CategoryEntity.create(category,null,"Comment race category","comment-race-"+category,
                    CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0));
            var product=CatalogItemEntity.create(item,merchant,CatalogItemType.PRODUCT,ListingKind.STANDARD,category,
                    "Comment race product","comment-race-"+item,"Unchanged commerce",now);
            product.publish(now);
            items.saveAndFlush(product);
            String authorToken=token(author),adminToken=token(admin),reporterToken=token(reporter);
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            String privateRoute="/api/v1/me/catalog/items/"+item+"/comments";
            var created=mvc.perform(post(privateRoute).header("Authorization",authorToken).contentType("application/json")
                    .content("{\"body\":\"Concurrent comment sentinel\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            UUID comment=UUID.fromString(mapper.readTree(created.getContentAsString()).path("id").asText());
            mvc.perform(post("/api/v1/reports").header("Authorization",reporterToken)
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content(mapper.writeValueAsString(java.util.Map.of("targetType","SOCIAL_COMMENT","targetId",comment,"reasonCode","SPAM"))))
                    .andExpect(status().isCreated());
            UUID caseId=jdbc.queryForObject("select id from moderation_cases where target_social_comment_id=?",UUID.class,comment);
            var beforeItem=jdbc.queryForMap("select * from catalog_items where id=?",item);
            String beforeComment=jdbc.queryForObject("select (to_jsonb(original)-'status'-'updated_at')::text from catalog_social_comments original where id=?",String.class,comment);
            String hideKey=UUID.randomUUID().toString();
            String hideRoute="/api/v1/admin/moderation-cases/"+caseId+"/hide-content";
            Callable<MockHttpServletResponse> remove=() -> mvc.perform(delete(privateRoute+"/"+comment)
                    .header("Authorization",authorToken)).andReturn().getResponse();
            Callable<MockHttpServletResponse> hide=() -> mvc.perform(post(hideRoute).header("Authorization",adminToken)
                    .header("Idempotency-Key",hideKey).contentType("application/json")
                    .content("{\"reasonCode\":\"SPAM\",\"note\":\"Contended moderation\"}")).andReturn().getResponse();
            MockHttpServletResponse removed,hidden;
            try (var blocker=jdbc.getDataSource().getConnection();var workers=Executors.newFixedThreadPool(2)) {
                blocker.setAutoCommit(false);
                try (var lock=blocker.prepareStatement("select id from catalog_social_comments where id=? for update")) {
                    lock.setObject(1,comment);
                    try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                var first=workers.submit(deleteFirst?remove:hide);
                java.util.concurrent.Future<MockHttpServletResponse> second;
                try {
                    awaitCommentLocks(1);
                    second=workers.submit(deleteFirst?hide:remove);
                    awaitCommentLocks(2);
                    mvc.perform(get("/api/v1/catalog/items/"+item+"/comments"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(comment.toString()));
                } finally { blocker.rollback(); }
                var firstResponse=first.get(15,TimeUnit.SECONDS);
                var secondResponse=second.get(15,TimeUnit.SECONDS);
                removed=deleteFirst?firstResponse:secondResponse;
                hidden=deleteFirst?secondResponse:firstResponse;
            }
            assertThat(removed.getStatus()).as(removed.getContentAsString()).isEqualTo(deleteFirst?204:404);
            assertThat(hidden.getStatus()).as(hidden.getContentAsString()).isEqualTo(deleteFirst?409:200);
            if (deleteFirst) assertThat(hidden.getContentAsString()).contains("MODERATION_TARGET_STATE_CONFLICT");
            else assertThat(removed.getContentAsString()).contains("RESOURCE_NOT_FOUND");
            assertThat(jdbc.queryForObject("select status from catalog_social_comments where id=?",String.class,comment))
                    .isEqualTo(deleteFirst?"DELETED":"HIDDEN");
            assertThat(jdbc.queryForObject("select (to_jsonb(original)-'status'-'updated_at')::text from catalog_social_comments original where id=?",String.class,comment)).isEqualTo(beforeComment);
            assertThat(jdbc.queryForMap("select * from catalog_items where id=?",item)).isEqualTo(beforeItem);
            assertThat(jdbc.queryForObject("select case_status from moderation_cases where id=?",String.class,caseId)).isEqualTo(deleteFirst?"OPEN":"RESOLVED");
            for (String query:List.of("select count(*) from moderation_actions where moderation_case_id=? and action_type='HIDE'",
                    "select count(*) from audit_logs where entity_id=? and action='MODERATION_HIDE'",
                    "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED' and payload->>'action'='HIDE'"))
                assertThat(jdbc.queryForObject(query,Long.class,caseId)).isEqualTo(deleteFirst?0L:1L);
            mvc.perform(get("/api/v1/catalog/items/"+item+"/comments"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",item.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$['"+item+"'].comments").value(0));
            mvc.perform(get("/api/v1/me/social/activity").param("type","COMMENTED").header("Authorization",authorToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            var committed=jdbc.queryForMap("select * from catalog_social_comments where id=?",comment);
            mvc.perform(post(hideRoute).header("Authorization",adminToken).header("Idempotency-Key",hideKey)
                    .contentType("application/json").content("{\"reasonCode\":\"SPAM\",\"note\":\"Contended moderation\"}"))
                    .andExpect(deleteFirst?status().isConflict():status().isOk());
            assertThat(jdbc.queryForMap("select * from catalog_social_comments where id=?",comment)).isEqualTo(committed);
            assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=? and action_type='HIDE'",Long.class,caseId)).isEqualTo(deleteFirst?0L:1L);
            observed.add(deleteFirst?"DELETE":"HIDE");
            System.out.println("Comment delete/hide first="+(deleteFirst?"DELETE":"HIDE")+" statuses="+removed.getStatus()+"/"+hidden.getStatus()+" final="+(deleteFirst?"DELETED":"HIDDEN"));
        }
        assertThat(observed).containsExactly("DELETE","HIDE");
    }

    private String token(UUID user) {
        return "Bearer "+issuer.issue(user,new DeviceMetadata("Test","Comment race")).accessToken().value();
    }

    private void awaitCommentLocks(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,wait_event,query from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%catalog_social_comments%' and (query ilike '%update catalog_social_comments%' or query ilike '%for update%')");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("actual comment UPDATE/SELECT FOR UPDATE contention").hasSize(expected);
        if (expected==2) {
            assertThat(waiting.stream().filter(row -> row.get("query").toString().contains("for update")).count()).isEqualTo(1L);
            assertThat(waiting.stream().filter(row -> row.get("query").toString().contains("update catalog_social_comments")).count()).isEqualTo(1L);
        }
        System.out.println("Comment delete/hide lock waiters="+expected+" events="+waiting.stream().map(row -> row.get("wait_event")).toList());
    }
}
