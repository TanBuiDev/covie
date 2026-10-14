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
    @Test void reportsAndModeratesIndividualCommentsWithoutHidingProducts() throws Exception {
        UUID itemId = item(true);
        String owner = user("Comment writer");
        String reporter = user("Report viewer");
        var posted = mvc.perform(post("/api/v1/me/catalog/items/" + itemId + "/comments")
                .header("Authorization",owner).contentType("application/json")
                .content("{\\"body\\":\\"Suspicious advertisement\\\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID commentId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(posted.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/reports").header("Authorization",reporter)
                .header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\\"targetType\\":\\"SOCIAL_COMMENT\\",\\"targetId\\":\\"" + commentId +
                        "\\",\\"reasonCode\\":\\"SPAM\\"}"))
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

}
