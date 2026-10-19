package vn.covie.social.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class CatalogSocialControllerIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired InitialSessionIssuer issuer;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired vn.covie.moderation.application.SocialCommentModerationTargetHandler commentModeration;
    private MockMvc mvc;

    @Test void newActivityIsDisabledByDefaultAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me/social/activity").param("type","LIKED")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/social/activity").param("type","LIKED").header("Authorization",user("Activity OFF")))
                .andExpect(status().isNotFound());
    }

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    private UUID item(boolean publish) {
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "CoVie Store",
                "social-" + merchantId, NOW));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null,
                "Social products", "social-" + UUID.randomUUID(), CategoryCatalogType.PRODUCT,
                CategoryStatus.ACTIVE, 0)).getId();
        UUID id = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(id, merchantId, CatalogItemType.PRODUCT,
                ListingKind.STANDARD, categoryId, "Sample product", "sample-" + id, null, NOW);
        if (publish) item.publish(NOW);
        items.saveAndFlush(item);
        return id;
    }

    private String user(String name) {
        UUID userId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId,name,NOW));
        return "Bearer " + issuer.issue(userId,new DeviceMetadata("Test","Social")).accessToken().value();
    }

    @Test void newPreviewsAreDisabledByDefaultWithoutDisablingExistingSummary() throws Exception {
        UUID itemId=item(true);
        mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",itemId.toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString())).andExpect(status().isOk());
    }

    @Test void supportsIdempotentReactionsAndPrivateViewerState() throws Exception {
        UUID itemId = item(true);
        String route = "/api/v1/me/catalog/items/" + itemId;
        String token = user("Social viewer");
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$['" + itemId + "'].likes").value(0));
        mvc.perform(put(route + "/likes").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put(route + "/likes").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.liked").value(true));
        mvc.perform(put(route + "/likes").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes").value(1));
        mvc.perform(put(route + "/saves").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(jsonPath("$['" + itemId + "'].liked").value(false));
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString())
                .header("Authorization",token))
                .andExpect(jsonPath("$['" + itemId + "'].liked").value(true));
        mvc.perform(delete(route + "/likes").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes").value(0));
    }

    @Test void commentsHaveAuthorOwnershipAndInvisibleItemsStayHidden() throws Exception {
        UUID id = item(true);
        UUID hidden = item(false);
        String owner = user("Comment author");
        String foreign = user("Other viewer");
        String post = "/api/v1/me/catalog/items/" + id + "/comments";
        mvc.perform(put("/api/v1/me/catalog/items/" + hidden + "/likes").header("Authorization",owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",hidden.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post(post).header("Authorization",owner).contentType("application/json")
                .content("{\"body\":\"   \"}")).andExpect(status().isUnprocessableEntity());
        var response = mvc.perform(post(post).header("Authorization",owner).contentType("application/json")
                .content("{\"body\":\"Is this still available?\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.own").value(true))
                .andReturn();
        String commentId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/v1/catalog/items/" + id + "/comments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].body").value("Is this still available?"))
                .andExpect(jsonPath("$.items[0].own").value(false));
        mvc.perform(delete(post + "/" + commentId).header("Authorization",foreign))
                .andExpect(status().isNotFound());
        mvc.perform(delete(post + "/" + commentId).header("Authorization",owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",id.toString()))
                .andExpect(jsonPath("$['" + id + "'].comments").value(0));
    }
    @Test void paginationContinuesAfterCursorCommentIsDeletedOrHidden() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        UUID authorId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(authorId,"Pagination author",NOW));
        String token = "Bearer " + issuer.issue(authorId,new DeviceMetadata("Test","Social")).accessToken().value();
        for (String cursorStatus : java.util.List.of("DELETED","HIDDEN")) {
            UUID itemId = item(true);
            UUID newestId = UUID.randomUUID();
            UUID olderId = UUID.randomUUID();
            UUID oldestId = UUID.randomUUID();
            var orderedIds = java.util.List.of(oldestId,olderId,newestId);
            for (int position = 0; position < orderedIds.size(); position++) {
                jdbc.update("""
                    insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,created_at)
                    values (?,?,?,?,?)
                    """,orderedIds.get(position),itemId,authorId,"Pagination comment",
                        java.sql.Timestamp.from(NOW.plusSeconds(position)));
            }
            String route = "/api/v1/catalog/items/" + itemId + "/comments";
            var first = mvc.perform(get(route).param("limit","1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(newestId.toString()))
                    .andReturn();
            String cursor = mapper.readTree(first.getResponse().getContentAsString()).path("nextCursor").asText();
            org.assertj.core.api.Assertions.assertThat(cursor).isEqualTo(newestId.toString());
            if ("DELETED".equals(cursorStatus)) {
                mvc.perform(delete("/api/v1/me/catalog/items/" + itemId + "/comments/" + cursor)
                        .header("Authorization",token)).andExpect(status().isNoContent());
            } else {
                commentModeration.apply(newestId,vn.covie.moderation.model.ModerationActionType.HIDE,NOW);
            }
            mvc.perform(get(route).param("limit","1").param("before",cursor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(olderId.toString()))
                    .andExpect(jsonPath("$.nextCursor").value(olderId.toString()));
            mvc.perform(get(route).param("limit","1").param("before",olderId.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(oldestId.toString()))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }
    }

    @Test void reportsAndModeratesIndividualCommentsWithoutHidingProducts() throws Exception {
        UUID itemId = item(true);
        String owner = user("Comment writer");
        String reporter = user("Report viewer");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var posted = mvc.perform(post("/api/v1/me/catalog/items/" + itemId + "/comments")
                .header("Authorization",owner).contentType("application/json")
                .content(mapper.writeValueAsString(java.util.Map.of("body","Suspicious advertisement"))))
                .andExpect(status().isCreated()).andReturn();
        UUID commentId = UUID.fromString(mapper.readTree(
                posted.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/reports").header("Authorization",reporter)
                .header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json")
                .content(mapper.writeValueAsString(java.util.Map.of("targetType","SOCIAL_COMMENT",
                        "targetId",commentId.toString(),"reasonCode","SPAM"))))
                .andExpect(status().isCreated());
        Integer total = jdbc.queryForObject("""
            select count(*) from moderation_cases where target_type='SOCIAL_COMMENT'
            and target_social_comment_id=? and case_type='USER_REPORT'
            """,Integer.class,commentId);
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(1);
        commentModeration.apply(commentId,vn.covie.moderation.model.ModerationActionType.HIDE,NOW);
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$['" + itemId + "'].comments").value(0));
        mvc.perform(get("/api/v1/catalog/items/" + itemId + "/comments"))
                .andExpect(jsonPath("$.items").isEmpty());
        commentModeration.apply(commentId,vn.covie.moderation.model.ModerationActionType.RESTORE,NOW);
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(jsonPath("$['" + itemId + "'].comments").value(1));
    }

    @Test void bulkHideRetainsDeletedCommentConflictWithoutAbortingOtherComments() throws Exception {
        UUID itemId=item(true),admin=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(admin,"Comment bulk admin",NOW));
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
        String administrator="Bearer "+issuer.issue(admin,new DeviceMetadata("Test","Comment bulk")).accessToken().value();
        String author=user("Bulk comment author"),reporter=user("Bulk comment reporter");
        String privateRoute="/api/v1/me/catalog/items/"+itemId+"/comments";
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var commentIds=new java.util.ArrayList<UUID>();
        var caseIds=new java.util.ArrayList<UUID>();
        for (String body:java.util.List.of("Deleted before bulk hide","Active during bulk hide")) {
            var response=mvc.perform(post(privateRoute).header("Authorization",author).contentType("application/json")
                    .content(mapper.writeValueAsString(java.util.Map.of("body",body))))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            UUID comment=UUID.fromString(mapper.readTree(response.getContentAsString()).path("id").asText());
            commentIds.add(comment);
            mvc.perform(post("/api/v1/reports").header("Authorization",reporter)
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content(mapper.writeValueAsString(java.util.Map.of("targetType","SOCIAL_COMMENT","targetId",comment,"reasonCode","SPAM"))))
                    .andExpect(status().isCreated());
            caseIds.add(jdbc.queryForObject("select id from moderation_cases where target_social_comment_id=?",UUID.class,comment));
        }
        mvc.perform(delete(privateRoute+"/"+commentIds.getFirst()).header("Authorization",author)).andExpect(status().isNoContent());
        var deleted=jdbc.queryForMap("select * from catalog_social_comments where id=?",commentIds.getFirst());
        var product=jdbc.queryForMap("select * from catalog_items where id=?",itemId);
        String key=UUID.randomUUID().toString();
        String payload=mapper.writeValueAsString(java.util.Map.of("action","HIDE","caseIds",caseIds,"reasonCode","SPAM"));
        String firstBody=null;
        for (int attempt=0;attempt<2;attempt++) {
            var response=mvc.perform(post("/api/v1/admin/moderation-cases/bulk-actions").header("Authorization",administrator)
                    .header("Idempotency-Key",key).contentType("application/json").content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(2))
                    .andExpect(jsonPath("$.results[0].caseId").value(caseIds.getFirst().toString()))
                    .andExpect(jsonPath("$.results[0].success").value(false))
                    .andExpect(jsonPath("$.results[0].errorCode").value("MODERATION_TARGET_STATE_CONFLICT"))
                    .andExpect(jsonPath("$.results[1].caseId").value(caseIds.getLast().toString()))
                    .andExpect(jsonPath("$.results[1].success").value(true))
                    .andExpect(jsonPath("$.results[1].status").value("RESOLVED")).andReturn().getResponse();
            if (firstBody==null) firstBody=response.getContentAsString();
            else org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).isEqualTo(firstBody);
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("select * from catalog_social_comments where id=?",commentIds.getFirst())).isEqualTo(deleted);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("select * from catalog_items where id=?",itemId)).isEqualTo(product);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status from catalog_social_comments where id=?",String.class,commentIds.getLast())).isEqualTo("HIDDEN");
        for (int position=0;position<caseIds.size();position++) {
            UUID caseId=caseIds.get(position);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select case_status from moderation_cases where id=?",String.class,caseId)).isEqualTo(position==0?"OPEN":"RESOLVED");
            for (String query:java.util.List.of("select count(*) from moderation_actions where moderation_case_id=? and action_type='HIDE'",
                    "select count(*) from audit_logs where entity_id=? and action='MODERATION_HIDE'",
                    "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED' and payload->>'action'='HIDE'"))
                org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(query,Long.class,caseId)).isEqualTo(position==0?0L:1L);
        }
    }

    @Test void rejectsGuestAndNonAdminAccessToModeratorOnlyCommentPreview() throws Exception {
        UUID itemId = item(true);
        String owner = user("Moderation preview author");
        String route = "/api/v1/me/catalog/items/" + itemId + "/comments";
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var created = mvc.perform(post(route).header("Authorization",owner)
                .contentType("application/json")
                .content(mapper.writeValueAsString(java.util.Map.of("body","Visible to moderator only"))))
                .andExpect(status().isCreated()).andReturn();
        String commentId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        String adminPath = "/api/v1/admin/catalog-social/comments/" + commentId;
        mvc.perform(get(adminPath)).andExpect(status().isUnauthorized());
        mvc.perform(get(adminPath).header("Authorization",owner)).andExpect(status().isForbidden());
    }

    @Test void blocksPublicSocialActivityForHiddenOrSuspendedMerchants() throws Exception {
        UUID itemId = item(true);
        String actor = user("Merchant visibility viewer");
        UUID merchantId = items.findById(itemId).orElseThrow().getMerchantProfileId();
        var merchant = merchants.findById(merchantId).orElseThrow();
        merchant.hide(NOW);
        merchants.saveAndFlush(merchant);
        String summary = "/api/v1/catalog/social-summary";
        String likes = "/api/v1/me/catalog/items/" + itemId + "/likes";
        mvc.perform(get(summary).param("itemIds",itemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/catalog/items/" + itemId + "/comments"))
                .andExpect(status().isNotFound());
        mvc.perform(put(likes).header("Authorization",actor))
                .andExpect(status().isNotFound());

        // Use an independent persisted fixture: MockMvc requests are executed in
        // separate transactions and can invalidate a previously loaded JPA version.
        UUID suspendedItemId = item(true);
        UUID suspendedMerchantId = items.findById(suspendedItemId).orElseThrow().getMerchantProfileId();
        var suspendedMerchant = merchants.findById(suspendedMerchantId).orElseThrow();
        suspendedMerchant.suspend(NOW);
        merchants.saveAndFlush(suspendedMerchant);
        mvc.perform(get(summary).param("itemIds",suspendedItemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/api/v1/me/catalog/items/" + suspendedItemId + "/likes")
                .header("Authorization",actor)).andExpect(status().isNotFound());
    }

}
