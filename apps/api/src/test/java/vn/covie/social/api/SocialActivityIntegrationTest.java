package vn.covie.social.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
@SpringBootTest(properties={"covie.social-v2.activity-enabled=true","covie.outbox.max-events-per-poll=0"})
class SocialActivityIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired MerchantProfileRepository merchants;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired vn.covie.media.persistence.MediaAssetRepository assets;
    @Autowired CatalogItemMediaRepository mediaLinks;
    private static final String ROUTE="/api/v1/me/social/activity";
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();

    private record Actor(UUID id,String token) {}
    private Actor actor() {
        UUID id=UUID.randomUUID(); users.saveAndFlush(UserEntity.active(id,"Activity buyer",Instant.now()));
        return new Actor(id,"Bearer "+issuer.issue(id,new DeviceMetadata("Test","Activity")).accessToken().value());
    }
    private CatalogItemEntity item() {
        Instant now=Instant.now(); UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Activity merchant","activity-"+merchantId,now));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Activity category",
                "activity-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var item=CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Activity item","activity-"+UUID.randomUUID(),null,now);
        item.publish(now); return items.saveAndFlush(item);
    }
    private com.fasterxml.jackson.databind.JsonNode activity(Actor actor,String type) throws Exception {
        return json.readTree(mvc.perform(get(ROUTE).header("Authorization",actor.token()).param("type",type))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test void everyActivityTypeExcludesRestrictedCatalogMerchantAndCommentRowsWithoutWriting() throws Exception {
        var owner=actor();
        var allowed=new ArrayList<String>();
        for (String state : java.util.List.of("PUBLIC","APPROVED","ITEM_DRAFT","ITEM_PAUSED","ITEM_DELETED","ITEM_SOFT_DELETED",
                "ITEM_PENDING","ITEM_REJECTED","ITEM_HIDDEN","MERCHANT_PAUSED","MERCHANT_SUSPENDED","MERCHANT_DELETED",
                "MERCHANT_SOFT_DELETED","MERCHANT_PENDING","MERCHANT_REJECTED","MERCHANT_HIDDEN")) {
            var entry=item();
            boolean visible=state.equals("PUBLIC") || state.equals("APPROVED");
            String marker=visible?"allowed-"+state:"restricted-"+state;
            jdbc.update("update catalog_items set name=? where id=?",marker,entry.getId());
            jdbc.update("update merchant_profiles set name=? where id=?",marker,entry.getMerchantProfileId());
            for (String kind : java.util.List.of("LIKE","SAVE"))
                jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind) values (?,?,?)",entry.getId(),owner.id(),kind);
            jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,status) values (?,?,?,?,'ACTIVE')",
                    UUID.randomUUID(),entry.getId(),owner.id(),marker);
            if (visible) allowed.add(entry.getId().toString());
            switch (state) {
                case "APPROVED" -> {
                    jdbc.update("update catalog_items set moderation_status='APPROVED' where id=?",entry.getId());
                    jdbc.update("update merchant_profiles set moderation_status='APPROVED' where id=?",entry.getMerchantProfileId());
                }
                case "ITEM_DRAFT","ITEM_PAUSED","ITEM_DELETED" ->
                    jdbc.update("update catalog_items set listing_status=? where id=?",state.substring(5),entry.getId());
                case "ITEM_SOFT_DELETED" -> jdbc.update("update catalog_items set deleted_at=now() where id=?",entry.getId());
                case "ITEM_PENDING","ITEM_REJECTED","ITEM_HIDDEN" ->
                    jdbc.update("update catalog_items set moderation_status=? where id=?",state.substring(5),entry.getId());
                case "MERCHANT_PAUSED","MERCHANT_SUSPENDED","MERCHANT_DELETED" ->
                    jdbc.update("update merchant_profiles set merchant_status=? where id=?",state.substring(9),entry.getMerchantProfileId());
                case "MERCHANT_SOFT_DELETED" -> jdbc.update("update merchant_profiles set deleted_at=now() where id=?",entry.getMerchantProfileId());
                case "MERCHANT_PENDING","MERCHANT_REJECTED","MERCHANT_HIDDEN" ->
                    jdbc.update("update merchant_profiles set moderation_status=? where id=?",state.substring(9),entry.getMerchantProfileId());
                default -> { }
            }
            if (state.equals("PUBLIC")) for (String status : java.util.List.of("HIDDEN","DELETED"))
                jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,status) values (?,?,?,?,?)",
                        UUID.randomUUID(),entry.getId(),owner.id(),"restricted-comment-"+status,status);
        }
        var before=activityBusinessSnapshot();
        for (String type : java.util.List.of("LIKED","SAVED","COMMENTED")) {
            var response=activity(owner,type);
            var returned=new ArrayList<String>();
            response.path("items").forEach(row -> returned.add(row.path("itemId").asText()));
            assertThat(returned).as(type).containsExactlyInAnyOrderElementsOf(allowed);
            assertThat(response.toString()).doesNotContain("restricted-");
            assertThat(response.path("nextCursor").isMissingNode() || response.path("nextCursor").isNull()).isTrue();
        }
        assertThat(activityBusinessSnapshot()).isEqualTo(before);
    }

    private java.util.Map<String,java.util.List<String>> activityBusinessSnapshot() {
        return java.util.Map.of(
                "items",jdbc.queryForList("select to_jsonb(row)::text from catalog_items row order by id",String.class),
                "merchants",jdbc.queryForList("select to_jsonb(row)::text from merchant_profiles row order by id",String.class),
                "reactions",jdbc.queryForList("select to_jsonb(row)::text from catalog_social_reactions row order by catalog_item_id,user_id,kind",String.class),
                "comments",jdbc.queryForList("select to_jsonb(row)::text from catalog_social_comments row order by id",String.class));
    }

    @Test void commentCursorSurvivesDeletedAnchorAndFiltersHiddenRowsAndNewerInserts() throws Exception {
        var owner=actor(); var other=actor(); var entry=item();
        var timestamp=java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
        for (int index=0;index<25;index++)
            jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,created_at,updated_at) values (?,?,?,'Tied comment',?,?)",
                    UUID.randomUUID(),entry.getId(),owner.id(),timestamp,timestamp);
        var expected=new ArrayList<>(jdbc.queryForList("select id from catalog_social_comments where author_user_id=? order by created_at desc,id desc",UUID.class,owner.id()));
        var first=json.readTree(mvc.perform(get(ROUTE).header("Authorization",owner.token()).param("type","COMMENTED").param("limit","10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.path("items")).hasSize(10);
        var observed=new ArrayList<UUID>();
        first.path("items").forEach(row -> observed.add(UUID.fromString(row.path("commentId").asText())));
        assertThat(observed).containsExactlyElementsOf(expected.subList(0,10));
        jdbc.update("update catalog_social_comments set status='DELETED' where id=?",expected.get(9));
        UUID hidden=expected.remove(20);
        jdbc.update("update catalog_social_comments set status='HIDDEN' where id=?",hidden);
        UUID newer=UUID.randomUUID(); UUID foreign=UUID.randomUUID();
        jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body) values (?,?,?,'Newer comment')",
                newer,entry.getId(),owner.id());
        jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,created_at) values (?,?,?,'Foreign comment',?)",
                foreign,entry.getId(),other.id(),java.sql.Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")));
        var before=activityBusinessSnapshot();
        String cursor=first.path("nextCursor").asText();
        for (int count : new int[]{10,4}) {
            var page=json.readTree(mvc.perform(get(ROUTE).header("Authorization",owner.token()).param("type","COMMENTED")
                    .param("limit","10").param("cursor",cursor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(page.path("items")).hasSize(count);
            page.path("items").forEach(row -> observed.add(UUID.fromString(row.path("commentId").asText())));
            cursor=page.path("nextCursor").isTextual()?page.path("nextCursor").asText():null;
        }
        assertThat(cursor).isNull();
        assertThat(observed).containsExactlyElementsOf(expected).doesNotHaveDuplicates().doesNotContain(hidden,newer,foreign);
        var otherActivity=activity(other,"COMMENTED").path("items");
        assertThat(otherActivity).hasSize(1);
        assertThat(otherActivity.get(0).path("commentId").asText()).isEqualTo(foreign.toString());
        assertThat(activityBusinessSnapshot()).isEqualTo(before);
    }

    @Test void currentReactionsAndEachActiveCommentArePrivateAndRespectVisibility() throws Exception {
        var owner=actor(); var other=actor(); var item=item();
        var pending=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),owner.id(),"activity-pending-"+UUID.randomUUID(),
                vn.covie.media.model.MediaPurpose.CATALOG_IMAGE,"image/png",128,Instant.now().plusSeconds(600),Instant.now());
        var ready=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),owner.id(),"activity-ready-"+UUID.randomUUID(),
                vn.covie.media.model.MediaPurpose.CATALOG_IMAGE,"image/png",128,Instant.now().plusSeconds(600),Instant.now());
        ready.markReady(Instant.now()); assets.saveAndFlush(pending); assets.saveAndFlush(ready);
        mediaLinks.saveAndFlush(CatalogItemMediaEntity.image(item,pending,0,true));
        mediaLinks.saveAndFlush(CatalogItemMediaEntity.image(item,ready,1,false));
        mvc.perform(get(ROUTE).param("type","LIKED")).andExpect(status().isUnauthorized());
        String commands="/api/v1/me/catalog/items/"+item.getId();
        mvc.perform(put(commands+"/likes").header("Authorization",owner.token())).andExpect(status().isOk());
        mvc.perform(put(commands+"/saves").header("Authorization",owner.token())).andExpect(status().isOk());
        var liked=activity(owner,"LIKED").path("items");
        assertThat(liked).hasSize(1);
        assertThat(liked.get(0).path("coverUrl").asText()).isEqualTo("https://media.test.covie/"+ready.getStorageKey());
        assertThat(liked.get(0).has("storageKey")).isFalse();
        assertThat(liked.get(0).path("itemAvailable").asBoolean()).isTrue();
        assertThat(activity(owner,"SAVED").path("items")).hasSize(1);
        assertThat(activity(other,"LIKED").path("items")).isEmpty();
        mvc.perform(get(ROUTE).header("Authorization",other.token()).param("type","LIKED").param("userId",owner.id().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        var commentIds=new ArrayList<String>();
        for (int index=0;index<2;index++) commentIds.add(json.readTree(mvc.perform(post(commands+"/comments")
                .header("Authorization",owner.token()).contentType("application/json").content("{\"body\":\"Comment <script>literal</script>\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText());
        var comments=activity(owner,"COMMENTED").path("items");
        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).path("commentExcerpt").asText()).isEqualTo("Comment <script>literal</script>");
        mvc.perform(delete(commands+"/comments/"+commentIds.getFirst()).header("Authorization",owner.token())).andExpect(status().isNoContent());
        assertThat(activity(owner,"COMMENTED").path("items")).hasSize(1);
        mvc.perform(delete(commands+"/likes").header("Authorization",owner.token())).andExpect(status().isOk());
        mvc.perform(delete(commands+"/saves").header("Authorization",owner.token())).andExpect(status().isOk());
        assertThat(activity(owner,"LIKED").path("items")).isEmpty();
        assertThat(activity(owner,"SAVED").path("items")).isEmpty();
        item.hide(Instant.now()); items.saveAndFlush(item);
        assertThat(activity(owner,"COMMENTED").path("items")).isEmpty();
    }

    @Test void timestampTiesPageWithoutDuplicatesAndCursorsBindActorAndType() throws Exception {
        var owner=actor(); var other=actor();
        for (int index=0;index<25;index++) jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind,created_at) values (?,?,'LIKE',?)",
                item().getId(),owner.id(),java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        var before=jdbc.queryForList("select * from catalog_social_reactions where user_id=? order by catalog_item_id",owner.id());
        var first=activity(owner,"LIKED"); assertThat(first.path("items")).hasSize(20);
        String cursor=first.path("nextCursor").asText(); assertThat(cursor).isNotBlank();
        var second=json.readTree(mvc.perform(get(ROUTE).header("Authorization",owner.token()).param("type","LIKED").param("cursor",cursor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.path("items")).hasSize(5);
        var ids=new ArrayList<String>();
        first.path("items").forEach(row -> ids.add(row.path("itemId").asText()));
        second.path("items").forEach(row -> ids.add(row.path("itemId").asText()));
        assertThat(ids).hasSize(25).doesNotHaveDuplicates();
        for (var actor : new Actor[]{owner,other}) mvc.perform(get(ROUTE).header("Authorization",actor.token())
                .param("type",actor==owner?"SAVED":"LIKED").param("cursor",cursor)).andExpect(status().isUnprocessableEntity());
        for (String limit : new String[]{"0","51"}) mvc.perform(get(ROUTE).header("Authorization",owner.token())
                .param("type","LIKED").param("limit",limit)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROUTE).header("Authorization",owner.token()).param("type","LIKED").param("cursor","broken")).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForList("select * from catalog_social_reactions where user_id=? order by catalog_item_id",owner.id())).isEqualTo(before);
    }
}
