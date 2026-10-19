package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.CategoryRepository;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
class CatalogCaptionFeatureDisabledIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired vn.covie.merchants.persistence.MerchantLocationRepository locations;
    @Autowired vn.covie.media.persistence.MediaAssetRepository assets;

    @Test void videoOffRejectsNewAttachmentsAndKeepsStoredVideoDuringUnrelatedEditsWithoutExposingIt() throws Exception {
        UUID owner=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner,"Video OFF seller",Instant.now()));
        UUID merchant=UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Video OFF shop","off-"+merchant,Instant.now()));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner,MerchantRole.OWNER,Instant.now()));
        UUID category=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Video OFF","off-"+merchant,CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        UUID location=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 CoVie",10.8,106.7,Instant.now())).getId();
        String bearer="Bearer "+issuer.issue(owner,new DeviceMetadata("Test","Video OFF")).accessToken().value();
        var image=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),owner,"off/image",vn.covie.media.model.MediaPurpose.CATALOG_IMAGE,"image/png",24,Instant.now().plusSeconds(600),Instant.now());
        image.markReady(Instant.now()); assets.saveAndFlush(image);
        var video=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),owner,"off/private-video",vn.covie.media.model.MediaPurpose.CATALOG_VIDEO,"video/mp4",24,Instant.now().plusSeconds(600),Instant.now());
        video.recordVideoMetadata(64,48,1000,"h264"); video.markReady(Instant.now()); assets.saveAndFlush(video);
        String route="/api/v1/merchants/"+merchant+"/catalog-items";
        String template="{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\""+category+"\",\"name\":\"Video OFF item\",\"mediaIds\":[\"%s\"],\"locationOffers\":[{\"merchantLocationId\":\""+location+"\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
        mvc.perform(post(route).header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(template.formatted(video.getId()))).andExpect(status().isUnprocessableEntity());
        var created=mvc.perform(post(route).header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(template.formatted(image.getId()))).andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText());
        jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'VIDEO',1,false)",id,video.getId());
        var links=jdbc.queryForList("select to_jsonb(cm)::text from catalog_item_media cm where catalog_item_id=? order by sort_order",String.class,id);
        mvc.perform(patch(route+"/"+id).header("Authorization",bearer).header("If-Match","\"v0\"")
                .contentType("application/json").content("{\"name\":\"Unrelated edit\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("select to_jsonb(cm)::text from catalog_item_media cm where catalog_item_id=? order by sort_order",String.class,id)).isEqualTo(links);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route+"/"+id).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.media.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.media[0].id").value(image.getId().toString()));
        jdbc.update("update catalog_items set listing_status='ACTIVE',moderation_status='APPROVED' where id=?",id);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/catalog/items/"+id).param("locationId",location.toString()))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.media.length()").value(1));
    }

    @Test void disabledCaptionRejectsDirectWritesButKeepsOldPatchesWorking() throws Exception {
        Instant now=Instant.now(); UUID ownerId=UUID.randomUUID(),merchantId=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(ownerId,"Caption owner",now));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId,"Caption shop","caption-shop-"+merchantId,now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchantId,ownerId,MerchantRole.OWNER,now));
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Caption category",
                "caption-category-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        UUID itemId=UUID.randomUUID();
        items.saveAndFlush(CatalogItemEntity.create(itemId,merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Original item","caption-item-"+itemId,"Commercial description",now));
        var before=jdbc.queryForMap("select * from catalog_items where id=?",itemId);
        String bearer="Bearer "+issuer.issue(ownerId,new DeviceMetadata("Caption","Flag OFF")).accessToken().value();
        String route="/api/v1/merchants/"+merchantId+"/catalog-items/"+itemId;
        mvc.perform(patch(route).header("Authorization",bearer).header("If-Match","\"v0\"")
                .contentType("application/json").content("{\"socialCaption\":\"Disabled caption\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("select * from catalog_items where id=?",itemId)).isEqualTo(before);
        mvc.perform(patch(route).header("Authorization",bearer).header("If-Match","\"v0\"")
                .contentType("application/json").content("{\"name\":\"Old patch still works\"}"))
                .andExpect(status().isNoContent());
        assertThat(items.findById(itemId).orElseThrow().getName()).isEqualTo("Old patch still works");
        UUID caseId=UUID.randomUUID();
        jdbc.update("update catalog_items set moderation_status='HIDDEN' where id=?",itemId);
        jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'PRIVATE_REASON','RESOLVED',now(),now())",caseId,itemId);
        jdbc.update("insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,metadata,created_at) values (?,?,'HIDE',?,'PRIVATE_REASON','{}'::jsonb,now())",UUID.randomUUID(),caseId,ownerId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.moderationReasonCode").doesNotExist());
        UUID locationId=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchantId,
                "Caption location","1 CoVie",10.8,106.7,now)).getId();
        String createRoute="/api/v1/merchants/"+merchantId+"/catalog-items";
        String oldBody="{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\""+categoryId
                +"\",\"name\":\"Old create\",\"locationOffers\":[{\"merchantLocationId\":\""+locationId
                +"\",\"variantClientKey\":\"__default__\",\"price\":25000,\"currency\":\"VND\",\"available\":true,\"inventoryMode\":\"UNTRACKED\"}]}";
        var legacyMedia=new java.util.ArrayList<UUID>();
        for (int index=0;index<11;index++) {
            var asset=vn.covie.media.model.MediaAssetEntity.pending(UUID.randomUUID(),ownerId,"legacy-gallery-"+UUID.randomUUID(),
                    vn.covie.media.model.MediaPurpose.CATALOG_IMAGE,"image/jpeg",128,now.plusSeconds(3600),now);
            asset.markReady(now); legacyMedia.add(assets.saveAndFlush(asset).getId());
        }
        oldBody="{\"mediaIds\":"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(legacyMedia)+","+oldBody.substring(1);
        String key=UUID.randomUUID().toString(); long countBefore=items.count();
        mvc.perform(post(createRoute).header("Authorization",bearer).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"socialCaption\":\"Disabled create caption\","+oldBody.substring(1)))
                .andExpect(status().isNotFound());
        assertThat(items.count()).isEqualTo(countBefore);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where operation=?",Long.class,
                "CATALOG_ITEM_CREATE:"+merchantId)).isZero();
        mvc.perform(post(createRoute).header("Authorization",bearer).header("Idempotency-Key",key)
                .contentType("application/json").content(oldBody)).andExpect(status().isCreated());
        assertThat(items.count()).isEqualTo(countBefore+1);
        assertThat(jdbc.queryForObject("select count(*) from catalog_item_media where catalog_item_id in (select id from catalog_items where merchant_profile_id=?)",Long.class,merchantId)).isEqualTo(11);
    }
}
