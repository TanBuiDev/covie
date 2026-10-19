package vn.covie.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
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
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.shared.security.SystemRole;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties = { "covie.social-v2.admin-reads-enabled=true", "covie.social-v2.admin-case-opening-enabled=true", "covie.outbox.max-events-per-poll=0" })
class AdminCatalogReadIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SystemRoleAssignmentRepository roles;
    @Autowired InitialSessionIssuer issuer;
    @Autowired MerchantProfileRepository merchants;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired CatalogItemMediaRepository itemMedia;
    @Autowired MediaAssetRepository assets;
    @Autowired vn.covie.moderation.persistence.ModerationCaseRepository moderationCases;
    @Autowired vn.covie.merchants.persistence.MerchantMembershipRepository memberships;
    @Autowired vn.covie.merchants.persistence.MerchantLocationRepository locations;
    @Autowired VariantRepository variants;
    @Autowired LocationVariantRepository offers;
    @Autowired vn.covie.outbox.application.OutboxProcessor outbox;

    private String token(boolean admin) {
        UUID userId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId,"Catalog reader",Instant.now()));
        if (admin) roles.saveAndFlush(SystemRoleAssignmentEntity.assign(
                UUID.randomUUID(),userId,SystemRole.ADMIN,Instant.now()));
        return "Bearer " + issuer.issue(userId,new DeviceMetadata("Test","Admin catalog")).accessToken().value();
    }

    private CatalogItemEntity item(UUID merchantId, UUID categoryId, String name, boolean deleted) {
        var item = CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,
                ListingKind.RESALE,categoryId,name,"admin-catalog-" + UUID.randomUUID(),
                "Commercial description",Instant.now());
        item.publish(Instant.now());
        if (deleted) item.delete(Instant.now());
        else item.hide(Instant.now());
        return items.saveAndFlush(item);
    }

    @Test void summaryReportsStoredCaptionPresenceWithoutReturningItsText() throws Exception {
        UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Caption metadata","caption-meta-"+merchantId,Instant.now()));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,
                "Metadata category","metadata-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var caption=item(merchantId,categoryId,"With caption",false);
        caption.updateSocialCaption("Literal <b>caption</b>\nSecond line",Instant.now());
        items.saveAndFlush(caption);
        var absent=item(merchantId,categoryId,"Description only",true);
        var before=jdbc.queryForList("select to_jsonb(original)::text from catalog_items original where merchant_profile_id=? order by id",String.class,merchantId);
        String admin=token(true);
        String route="/api/v1/admin/catalog-items";
        var response=mvc.perform(get(route).param("merchantId",merchantId.toString()).header("Authorization",admin))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("items")).hasSize(2);
        for (var entry:body.path("items")) {
            assertThat(entry.path("hasCaption").isBoolean()).isTrue();
            assertThat(entry.path("hasCaption").asBoolean()).isEqualTo(entry.path("id").asText().equals(caption.getId().toString()));
            assertThat(entry.has("socialCaption")).isFalse();
            assertThat(entry.path("hasVideo").isBoolean()).isTrue();
            assertThat(entry.path("hasVideo").asBoolean()).isFalse();
        }
        mvc.perform(get(route+"/"+caption.getId()).header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.item.hasCaption").value(true)).andExpect(jsonPath("$.socialCaption").value("Literal <b>caption</b>\nSecond line"));
        mvc.perform(get(route+"/"+absent.getId()).header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.item.hasCaption").value(false)).andExpect(jsonPath("$.description").value("Commercial description"));
        assertThat(jdbc.queryForList("select to_jsonb(original)::text from catalog_items original where merchant_profile_id=? order by id",String.class,merchantId)).isEqualTo(before);
    }

    @Test void summaryReportsLinkedVideoPresenceWithoutExposingDisabledVideoContent() throws Exception {
        UUID merchantId=UUID.randomUUID(),owner=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner,"Video metadata owner",Instant.now()));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Video metadata","video-meta-"+merchantId,Instant.now()));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Video category",
                "video-meta-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var withVideo=item(merchantId,categoryId,"Linked video",false);
        var deletedVideo=item(merchantId,categoryId,"Deleted video",false);
        var noVideo=item(merchantId,categoryId,"No video",false);
        for(var entry:java.util.List.of(withVideo,deletedVideo)) {
            var video=MediaAssetEntity.pending(UUID.randomUUID(),owner,"metadata/"+UUID.randomUUID(),
                    MediaPurpose.CATALOG_VIDEO,"video/mp4",128,Instant.now().plusSeconds(600),Instant.now());
            video.recordVideoMetadata(64,48,1000,"h264");
            video.markReady(Instant.now());
            assets.saveAndFlush(video);
            jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'VIDEO',0,false)",entry.getId(),video.getId());
            if(entry.getId().equals(deletedVideo.getId())) jdbc.update("update media_assets set deleted_at=now() where id=?",video.getId());
        }
        var before=jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner);
        String admin=token(true);
        var response=mvc.perform(get("/api/v1/admin/catalog-items").param("merchantId",merchantId.toString()).header("Authorization",admin))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("items")).hasSize(3);
        for(var entry:body.path("items")) {
            assertThat(entry.path("hasVideo").isBoolean()).isTrue();
            assertThat(entry.path("hasVideo").asBoolean()).isEqualTo(entry.path("id").asText().equals(withVideo.getId().toString()));
        }
        mvc.perform(get("/api/v1/admin/catalog-items/"+withVideo.getId()).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.hasVideo").value(true)).andExpect(jsonPath("$.media").isEmpty());
        assertThat(response.getContentAsString()).doesNotContain("storageKey","metadata/","/content");
        assertThat(jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner)).isEqualTo(before);
    }

    @Test void adminReadsHiddenAndDeletedItemsWithoutChangingThem() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String name = "Read-only " + UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,name,"reader-" + merchantId,Instant.now()));
        UUID categoryId = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,
                name,"reader-category-" + UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var hidden = item(merchantId,categoryId,name,false);
        var deleted = item(merchantId,categoryId,name,true);
        hidden.updateSocialCaption("Hidden caption\nsecond line",Instant.now());
        deleted.updateSocialCaption("<script>Plain caption</script>\nsecond line",Instant.now());
        items.saveAndFlush(hidden); items.saveAndFlush(deleted);
        UUID mediaOwner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(mediaOwner,"Media owner",Instant.now()));
        var ready = MediaAssetEntity.pending(UUID.randomUUID(),mediaOwner,"catalog-ready-" + UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE,"image/jpeg",128,Instant.now().plusSeconds(3600),Instant.now());
        ready.markReady(Instant.now());
        var pending = MediaAssetEntity.pending(UUID.randomUUID(),mediaOwner,"catalog-pending-" + UUID.randomUUID(),
                MediaPurpose.CATALOG_IMAGE,"image/jpeg",128,Instant.now().plusSeconds(3600),Instant.now());
        assets.saveAllAndFlush(java.util.List.of(ready,pending));
        itemMedia.saveAllAndFlush(java.util.List.of(CatalogItemMediaEntity.image(deleted,ready,0,true),
                CatalogItemMediaEntity.image(deleted,pending,1,false)));
        var before = jdbc.queryForList("select * from catalog_items where merchant_profile_id=? order by id",merchantId);
        var mediaBefore = jdbc.queryForList("select * from media_assets where owner_user_id=? order by id",mediaOwner);
        String admin = token(true);
        String route = "/api/v1/admin/catalog-items";
        mvc.perform(get(route).param("merchantId",merchantId.toString()).param("size","1")
                .param("sort","CREATED_ASC").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(hidden.getId().toString()));
        mvc.perform(get(route).param("merchantId",merchantId.toString()).param("size","1")
                .param("page","1").param("sort","CREATED_ASC").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(deleted.getId().toString()));
        mvc.perform(get(route).param("q",merchantId.toString()).param("listingKind","RESALE")
                .param("moderationStatus","HIDDEN").param("listingStatus","ACTIVE")
                .header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(route + "/" + deleted.getId()).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.listingStatus").value("DELETED"))
                .andExpect(jsonPath("$.description").value("Commercial description"))
                .andExpect(jsonPath("$.socialCaption").value("<script>Plain caption</script>\nsecond line"))
                .andExpect(jsonPath("$.item.primaryMedia.id").value(ready.getId().toString()))
                .andExpect(jsonPath("$.media[0].status").value("READY"))
                .andExpect(jsonPath("$.media[0].url").isString())
                .andExpect(jsonPath("$.media[1].status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.media[1].url").doesNotExist())
                .andExpect(jsonPath("$.media[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get(route+"/"+hidden.getId()).header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.socialCaption").value("Hidden caption\nsecond line"))
                .andExpect(jsonPath("$.description").value("Commercial description"));
        assertThat(jdbc.queryForList("select * from catalog_items where merchant_profile_id=? order by id",merchantId))
                .isEqualTo(before);
        assertThat(jdbc.queryForList("select * from media_assets where owner_user_id=? order by id",mediaOwner)).isEqualTo(mediaBefore);
    }

    @Test void caseHistoryContinuesBeyondDetailLimitWithoutShiftingOnNewReports() throws Exception {
        UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Case shop","case-shop-" + merchantId,Instant.now()));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Case category",
                "case-category-" + UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        UUID itemId=item(merchantId,categoryId,"Case history",false).getId();
        UUID otherId=item(merchantId,categoryId,"Other history",false).getId();
        UUID reporterId=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(reporterId,"Case reporter",Instant.now()));
        Instant openedAt=Instant.parse("2026-10-15T00:00:00Z");
        for (int index=1; index<=52; index++) {
            UUID caseId=UUID.fromString("00000000-0000-7000-8000-" + String.format("%012d",index));
            moderationCases.save(vn.covie.moderation.model.ModerationCaseEntity.userReport(caseId,reporterId,
                    new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM,itemId),
                    "SPAM",null,openedAt));
        }
        moderationCases.saveAndFlush(vn.covie.moderation.model.ModerationCaseEntity.userReport(UUID.randomUUID(),reporterId,
                new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM,otherId),
                "SPAM",null,openedAt));
        var before=jdbc.queryForList("select * from moderation_cases where target_catalog_item_id=? order by id",itemId);
        String admin=token(true);
        String route="/api/v1/admin/catalog-items/" + itemId;
        var first=mvc.perform(get(route).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moderationCases.length()").value(50))
                .andExpect(jsonPath("$.moderationCaseTotal").value(52))
                .andExpect(jsonPath("$.moderationCaseNextCursor").isString()).andReturn();
        String cursor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString())
                .path("moderationCaseNextCursor").asText();
        UUID newestId=UUID.randomUUID();
        moderationCases.saveAndFlush(vn.covie.moderation.model.ModerationCaseEntity.userReport(newestId,reporterId,
                new vn.covie.shared.moderation.ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.CATALOG_ITEM,itemId),
                "SPAM",null,openedAt.plusSeconds(1)));
        mvc.perform(get(route + "/moderation-cases").param("cursor",cursor).param("limit","50").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].id").value("00000000-0000-7000-8000-000000000002"))
                .andExpect(jsonPath("$.items[1].id").value("00000000-0000-7000-8000-000000000001"));
        mvc.perform(get("/api/v1/admin/catalog-items/" + otherId + "/moderation-cases")
                .param("cursor",cursor).header("Authorization",admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route + "/moderation-cases").param("cursor","invalid").header("Authorization",admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route + "/moderation-cases").param("limit","101").header("Authorization",admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route + "/moderation-cases")).andExpect(status().isUnauthorized());
        mvc.perform(get(route + "/moderation-cases").header("Authorization",token(false))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("select * from moderation_cases where target_catalog_item_id=? and id<>? order by id",itemId,newestId))
                .isEqualTo(before);
    }

    @Test void realReportHideRestoreFeedsAdminReadsPublicVisibilityAndOwnerNotifications() throws Exception {
        Instant now=Instant.now();
        UUID ownerId=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId,"Catalog owner",now));
        String owner="Bearer " + issuer.issue(ownerId,new DeviceMetadata("Test","Catalog owner")).accessToken().value();
        UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Moderation shop","moderation-shop-" + merchantId,now));
        memberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(),merchantId,
                ownerId,vn.covie.merchants.model.MerchantRole.OWNER,now));
        UUID locationId=locations.saveAndFlush(vn.covie.merchants.model.MerchantLocationEntity.create(UUID.randomUUID(),
                merchantId,"Main","1 CoVie",10.8,106.7,now)).getId();
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Moderation category",
                "moderation-category-" + UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var item=CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Moderated product","moderated-product-" + UUID.randomUUID(),"Original description",now);
        item.publish(now);
        items.saveAndFlush(item);
        UUID itemId=item.getId();
        UUID variantId=variants.saveAndFlush(VariantEntity.createDefault(UUID.randomUUID(),itemId,"Default",now)).getId();
        UUID offerId=offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),locationId,variantId,25000,"VND",
                true,InventoryMode.UNTRACKED,null,now)).getId();
        var offerBefore=jdbc.queryForMap("select * from location_variants where id=?",offerId);
        String buyer=token(false);
        String admin=token(true);
        String publicDetail="/api/v1/catalog/items/" + itemId;
        String adminDetail="/api/v1/admin/catalog-items/" + itemId;
        mvc.perform(get(publicDetail).param("locationId",locationId.toString())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/catalog/items/" + itemId + "/comments").header("Authorization",buyer)
                .contentType("application/json").content("{\"body\":\"Is this available?\"}")).andExpect(status().isCreated());
        String reportBody="{\"targetType\":\"CATALOG_ITEM\",\"targetId\":\"" + itemId + "\",\"reasonCode\":\"SPAM\"}";
        String reportKey=UUID.randomUUID().toString();
        var reported=mvc.perform(post("/api/v1/reports").header("Authorization",buyer)
                .header("Idempotency-Key",reportKey).contentType("application/json").content(reportBody))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(reported.getResponse().getContentAsString()).path("caseId").asText());
        mvc.perform(post("/api/v1/reports").header("Authorization",buyer).header("Idempotency-Key",reportKey)
                .contentType("application/json").content(reportBody)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()));
        mvc.perform(get(adminDetail).header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationCases[0].id").value(caseId.toString()))
                .andExpect(jsonPath("$.moderationCases[0].status").value("OPEN"));
        String caseRoute="/api/v1/admin/moderation-cases/" + caseId;
        String hideKey=UUID.randomUUID().toString();
        for (int attempt=0; attempt<2; attempt++) mvc.perform(post(caseRoute + "/hide-content")
                .header("Authorization",admin).header("Idempotency-Key",hideKey)
                .contentType("application/json").content("{\"reasonCode\":\"SPAM\"}")).andExpect(status().isOk());
        mvc.perform(get(adminDetail).header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.item.moderationStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.moderationCases[0].status").value("RESOLVED"));
        mvc.perform(get(publicDetail).param("locationId",locationId.toString())).andExpect(status().isNotFound());
        mvc.perform(get(publicDetail + "/comments")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/catalog/items").param("categoryId",categoryId.toString()).param("lat","10.8").param("lng","106.7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.items").isEmpty());
        mvc.perform(get("/api/v1/merchants/" + merchantId + "/catalog-items/" + itemId).header("Authorization",owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moderationStatus").value("HIDDEN"));
        String restoreKey=UUID.randomUUID().toString();
        for (int attempt=0; attempt<2; attempt++) mvc.perform(post(caseRoute + "/restore-content")
                .header("Authorization",admin).header("Idempotency-Key",restoreKey)).andExpect(status().isOk());
        mvc.perform(get(adminDetail + "/moderation-cases").header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(caseId.toString()));
        mvc.perform(get(publicDetail).param("locationId",locationId.toString())).andExpect(status().isOk());
        mvc.perform(get(publicDetail + "/comments")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/catalog/social-summary").param("itemIds",itemId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$['" + itemId + "'].comments").value(1));
        mvc.perform(get("/api/v1/catalog/items").param("categoryId",categoryId.toString()).param("lat","10.8").param("lng","106.7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.items[0].id").value(itemId.toString()));
        assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action in ('MODERATION_HIDE','MODERATION_RESTORE')",Long.class,caseId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
        var events=jdbc.queryForList("select id from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'",UUID.class,caseId);
        assertThat(events).hasSize(2);
        for (int attempt=0; attempt<100 && outbox.processOne(); attempt++) {}
        for (UUID eventId : events) {
            assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,eventId)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("select count(*) from notifications where source_outbox_event_id=? and user_id=? and notification_type='MODERATION_UPDATED'",Long.class,eventId,ownerId)).isEqualTo(1);
        }
        assertThat(jdbc.queryForMap("select * from location_variants where id=?",offerId)).isEqualTo(offerBefore);
        assertThat(items.findById(itemId).orElseThrow().getDescription()).isEqualTo("Original description");
    }

    @Test void adminOpensAuditedCaseWithoutChangingItemAndReusesItOnReplay() throws Exception {
        Instant now=Instant.now();
        UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Admin flag shop","admin-flag-shop-" + merchantId,now));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Admin flag category",
                "admin-flag-category-" + UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var item=CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Unreported item","unreported-item-" + UUID.randomUUID(),"Original description",now);
        item.publish(now);
        items.saveAndFlush(item);
        UUID itemId=item.getId();
        var before=jdbc.queryForMap("select * from catalog_items where id=?",itemId);
        String admin=token(true);
        String route="/api/v1/admin/catalog-items/" + itemId + "/moderation-cases";
        String key=UUID.randomUUID().toString();
        String body="{\"reasonCode\":\"SPAM\",\"note\":\"Admin observed suspicious content\"}";
        mvc.perform(post(route).header("Authorization",admin).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"reasonCode\":\" \"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/admin/catalog-items/" + UUID.randomUUID() + "/moderation-cases")
                .header("Authorization",admin).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,itemId)).isZero();
        var opened=mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",key)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true)).andReturn();
        UUID caseId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(opened.getResponse().getContentAsString()).path("caseId").asText());
        mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",key)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString())).andExpect(jsonPath("$.created").value(true));
        mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString())).andExpect(jsonPath("$.created").value(false));
        mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"reasonCode\":\"OTHER\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",itemId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,itemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select case_type from moderation_cases where id=?",String.class,caseId)).isEqualTo("SYSTEM_FLAG");
        assertThat(jdbc.queryForObject("select description from moderation_cases where id=?",String.class,caseId))
                .isEqualTo("Admin observed suspicious content");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/catalog-items/" + itemId).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moderationCases[0].id").value(caseId.toString()));
        mvc.perform(post(route).contentType("application/json").content(body)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(route).header("Authorization",token(false)).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/moderation-cases/" + caseId + "/hide-content").header("Authorization",admin)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"reasonCode\":\"SPAM\"}")).andExpect(status().isOk());
        assertThat(items.findById(itemId).orElseThrow().getModerationStatus()).isEqualTo(CatalogModerationStatus.HIDDEN);
        mvc.perform(post(route).header("Authorization",admin).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body)).andExpect(status().isConflict());
    }

    @Test void concurrentAdminsReuseOneCaseWithoutMutatingItem() throws Exception {
        Instant now=Instant.now();
        UUID merchantId=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Concurrent flag shop","concurrent-flag-"+merchantId,now));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Concurrent flag category",
                "concurrent-flag-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var item=CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Concurrent item","concurrent-item-"+UUID.randomUUID(),"Original description",now);
        item.publish(now);
        items.saveAndFlush(item);
        UUID itemId=item.getId();
        var before=jdbc.queryForMap("select * from catalog_items where id=?",itemId);
        String firstAdmin=token(true), secondAdmin=token(true);
        String route="/api/v1/admin/catalog-items/"+itemId+"/moderation-cases";
        String body="{\"reasonCode\":\"SPAM\",\"note\":\"Concurrent Admin observation\"}";
        try (var blocker=jdbc.getDataSource().getConnection();
             var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock=blocker.prepareStatement("select id from catalog_items where id=? for update")) {
                lock.setObject(1,itemId);
                try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var first=workers.submit(() -> mvc.perform(post(route).header("Authorization",firstAdmin)
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content(body)).andReturn().getResponse());
            var second=workers.submit(() -> mvc.perform(post(route).header("Authorization",secondAdmin)
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content(body)).andReturn().getResponse());
            try {
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
                int waiting=0;
                while (System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() "
                            +"and wait_event_type='Lock' and query ilike '%catalog_items%'",Integer.class);
                    if (waiting>=2) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("both HTTP commands must reach the PostgreSQL item lock").isEqualTo(2);
            } finally { blocker.rollback(); }
            var firstResponse=first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var secondResponse=second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(firstResponse.getStatus()).isEqualTo(200);
            assertThat(secondResponse.getStatus()).isEqualTo(200);
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var firstResult=mapper.readTree(firstResponse.getContentAsString());
            var secondResult=mapper.readTree(secondResponse.getContentAsString());
            assertThat(firstResult.path("caseId").asText()).isEqualTo(secondResult.path("caseId").asText());
            assertThat(java.util.List.of(firstResult.path("created").asBoolean(),secondResult.path("created").asBoolean()))
                    .containsExactlyInAnyOrder(true,false);
            UUID caseId=UUID.fromString(firstResult.path("caseId").asText());
            assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=?",Long.class,itemId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1);
            assertThat(jdbc.queryForMap("select * from catalog_items where id=?",itemId)).isEqualTo(before);
        }
    }

    @Test void rejectsGuestAndNonAdminAndBoundsReadParameters() throws Exception {
        String route = "/api/v1/admin/catalog-items";
        mvc.perform(get(route)).andExpect(status().isUnauthorized());
        mvc.perform(get(route).header("Authorization",token(false))).andExpect(status().isForbidden());
        String admin = token(true);
        mvc.perform(get(route).param("page","-1").header("Authorization",admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route).param("size","101").header("Authorization",admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route).param("page","2147483647").header("Authorization",admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route).param("sort","name;drop table users").header("Authorization",admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route + "/" + UUID.randomUUID()).header("Authorization",admin)).andExpect(status().isNotFound());
    }
}
