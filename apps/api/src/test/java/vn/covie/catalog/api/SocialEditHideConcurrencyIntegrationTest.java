package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import vn.covie.settings.model.SettingValueType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.persistence.SystemSettingRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.caption-authoring-enabled=true","covie.social-v2.previews-enabled=true",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class SocialEditHideConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired SystemSettingRepository settings;

    @Test void concurrentCaptionGalleryEditAndHideHaveAtomicVersionedOutcomes() throws Exception {
        var winners=new ArrayList<String>();
        for (boolean sellerFirst:List.of(true,false)) {
            UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID();
            UUID item=UUID.randomUUID(),location=UUID.randomUUID(),variant=UUID.randomUUID(),caseId=UUID.randomUUID();
            UUID firstMedia=UUID.randomUUID(),secondMedia=UUID.randomUUID(),replacement=UUID.randomUUID();
            users.saveAllAndFlush(List.of(UserEntity.active(owner,"Race seller",Instant.now()),UserEntity.active(admin,"Race admin",Instant.now())));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Race shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"race-"+merchant);
            jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner);
            jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Race category',?,'PRODUCT','ACTIVE')",category,"race-"+category);
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Race product',?,'Commerce unchanged','Old caption','ACTIVE','APPROVED',now(),now())",item,merchant,category,"race-"+item);
            jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Race counter','Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
            jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",variant,item);
            jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),location,variant);
            for (UUID media:List.of(firstMedia,secondMedia,replacement)) jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'READY',now()+interval '1 hour',now(),now())",media,owner,"race-"+media);
            jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',0,true),(?,?,'IMAGE',1,false)",item,firstMedia,item,secondMedia);
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'SPAM','OPEN',now())",caseId,item);
            settings.saveAndFlush(SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",SettingValueType.ENUM,
                    new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),merchant,owner,Instant.now()));
            var originalGallery=gallery(item);
            var originalOffer=jdbc.queryForList("select to_jsonb(original)::text from location_variants original where variant_id=?",String.class,variant);
            var originalVariant=jdbc.queryForMap("select * from variants where id=?",variant);
            var originalAssets=jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner);
            String originalFields=jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item);
            String seller="Bearer "+issuer.issue(owner,new DeviceMetadata("Test","Race seller")).accessToken().value();
            String administrator="Bearer "+issuer.issue(admin,new DeviceMetadata("Test","Race admin")).accessToken().value();
            String body="{\"socialCaption\":\"New caption\",\"mediaIds\":[\""+replacement+"\",\""+firstMedia+"\"]}";
            Callable<MockHttpServletResponse> edit=() -> mvc.perform(patch("/api/v1/merchants/"+merchant+"/catalog-items/"+item)
                    .header("Authorization",seller).header("If-Match","\"v0\"").contentType("application/json").content(body)).andReturn().getResponse();
            Callable<MockHttpServletResponse> hide=() -> mvc.perform(post("/api/v1/admin/moderation-cases/"+caseId+"/hide-content")
                    .header("Authorization",administrator).header("Idempotency-Key",UUID.randomUUID().toString())
                    .contentType("application/json").content("{\"reasonCode\":\"SPAM\",\"note\":\"Race hide\"}")).andReturn().getResponse();
            MockHttpServletResponse editResponse,hideResponse;
            try (var blocker=jdbc.getDataSource().getConnection();var workers=Executors.newFixedThreadPool(2)) {
                blocker.setAutoCommit(false);
                try (var lock=blocker.prepareStatement("select id from catalog_items where id=? for no key update")) {
                    lock.setObject(1,item);
                    try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                var first=workers.submit(sellerFirst?edit:hide);
                java.util.concurrent.Future<MockHttpServletResponse> second;
                try {
                    awaitItemUpdates(1);
                    second=workers.submit(sellerFirst?hide:edit);
                    awaitItemUpdates(2);
                    var preview=mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",item.toString()))
                            .andExpect(status().isOk()).andReturn().getResponse();
                    var visible=new com.fasterxml.jackson.databind.ObjectMapper().readTree(preview.getContentAsString()).path(item.toString());
                    assertThat(visible.path("socialCaption").asText()).isEqualTo("Old caption");
                    var visibleMedia=new ArrayList<String>();
                    for (var entry:visible.path("media")) visibleMedia.add(entry.path("id").asText());
                    assertThat(visibleMedia).containsExactly(firstMedia.toString(),secondMedia.toString());
                } finally { blocker.rollback(); }
                var firstResponse=first.get(15,TimeUnit.SECONDS);
                var secondResponse=second.get(15,TimeUnit.SECONDS);
                editResponse=sellerFirst?firstResponse:secondResponse;
                hideResponse=sellerFirst?secondResponse:firstResponse;
            }
            boolean editWon=editResponse.getStatus()==204;
            assertThat(editResponse.getStatus()).as(editResponse.getContentAsString()).isIn(204,409);
            assertThat(hideResponse.getStatus()).as(hideResponse.getContentAsString()).isEqualTo(editWon?409:200);
            var conflict=editWon?hideResponse:editResponse;
            assertThat(conflict.getContentAsString()).contains("RESOURCE_VERSION_CONFLICT");
            String finalStatus=jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,item);
            assertThat(finalStatus).isEqualTo(editWon?"PENDING":"HIDDEN");
            assertThat(jdbc.queryForObject("select social_caption from catalog_items where id=?",String.class,item)).isEqualTo(editWon?"New caption":"Old caption");
            if (editWon) {
                var media=jdbc.queryForList("select media_asset_id from catalog_item_media where catalog_item_id=? order by sort_order",UUID.class,item);
                assertThat(media).containsExactly(replacement,firstMedia);
                assertThat(jdbc.queryForList("select sort_order from catalog_item_media where catalog_item_id=? order by sort_order",Integer.class,item)).containsExactly(0,1);
                assertThat(jdbc.queryForList("select is_primary from catalog_item_media where catalog_item_id=? order by sort_order",Boolean.class,item)).containsExactly(true,false);
            } else assertThat(gallery(item)).isEqualTo(originalGallery);
            assertThat(jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item)).isEqualTo(originalFields);
            assertThat(jdbc.queryForList("select to_jsonb(original)::text from location_variants original where variant_id=?",String.class,variant)).isEqualTo(originalOffer);
            assertThat(jdbc.queryForMap("select * from variants where id=?",variant)).isEqualTo(originalVariant);
            assertThat(jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner)).isEqualTo(originalAssets);
            long finalVersion=jdbc.queryForObject("select version from catalog_items where id=?",Long.class,item);
            assertThat(finalVersion).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",Long.class,item)).isEqualTo(editWon?1L:0L);
            assertThat(jdbc.queryForObject("select case_status from moderation_cases where id=?",String.class,caseId)).isEqualTo(editWon?"OPEN":"RESOLVED");
            for (String query:List.of("select count(*) from moderation_actions where moderation_case_id=?",
                    "select count(*) from audit_logs where entity_id=? and action='MODERATION_HIDE'",
                    "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED'"))
                assertThat(jdbc.queryForObject(query,Long.class,caseId)).isEqualTo(editWon?0L:1L);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id in (select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL') and event_type='MODERATION_CASE_OPENED'",Long.class,item)).isEqualTo(editWon?1L:0L);
            var hidden=mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",item.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(hidden.getContentAsString()).has(item.toString())).isFalse();
            mvc.perform(get("/api/v1/catalog/items/"+item).param("lat","10.8").param("lng","106.7")).andExpect(status().isNotFound());
            winners.add(editWon?"SELLER":"ADMIN");
            System.out.println("Social edit/hide started="+(sellerFirst?"SELLER":"ADMIN")+" statuses="+editResponse.getStatus()+"/"+hideResponse.getStatus()+" final="+finalStatus+" version="+finalVersion);
        }
        assertThat(winners).as("controlled scheduling must exercise both actual committed outcome branches").containsExactlyInAnyOrder("SELLER","ADMIN");
    }

    private List<String> gallery(UUID item) {
        return jdbc.queryForList("select to_jsonb(original)::text from catalog_item_media original where catalog_item_id=? order by sort_order",String.class,item);
    }

    private void awaitItemUpdates(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,wait_event,query from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%update catalog_items%'");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("actual versioned item UPDATE statements must both reach PostgreSQL contention").hasSize(expected);
        System.out.println("Social edit/hide waiting="+expected+" events="+waiting.stream().map(row -> row.get("wait_event")).toList());
    }
}
