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
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.catalog.api.SocialEditRestoreConcurrencyIntegrationTest$UpdateGateInspector"})
class SocialEditRestoreConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired SystemSettingRepository settings;

    @Test void editedHiddenOrRejectedContentCannotEscapeThroughConcurrentRestore() throws Exception {
        var observed=new ArrayList<String>();
        for (String initial:List.of("HIDDEN","REJECTED")) for (boolean sellerFirst:List.of(true,false)) {
            UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID();
            UUID item=UUID.randomUUID(),location=UUID.randomUUID(),variant=UUID.randomUUID(),caseId=UUID.randomUUID();
            UUID firstMedia=UUID.randomUUID(),secondMedia=UUID.randomUUID(),replacement=UUID.randomUUID();
            users.saveAllAndFlush(List.of(UserEntity.active(owner,"Restore race seller",Instant.now()),UserEntity.active(admin,"Restore race admin",Instant.now())));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Restore race shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"restore-race-"+merchant);
            jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner);
            jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Restore race category',?,'PRODUCT','ACTIVE')",category,"restore-race-"+category);
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Restore race product',?,'Commerce unchanged','Old reviewed caption','ACTIVE',?,now(),now())",item,merchant,category,"restore-race-"+item,initial);
            jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Restore race counter','Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
            jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",variant,item);
            jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),location,variant);
            for (UUID media:List.of(firstMedia,secondMedia,replacement)) jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'READY',now()+interval '1 hour',now(),now())",media,owner,"restore-race-"+media);
            jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',0,true),(?,?,'IMAGE',1,false)",item,firstMedia,item,secondMedia);
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,resolved_at) values (?,?,'CATALOG_ITEM',?,'SPAM','RESOLVED',now()-interval '1 hour',now()-interval '1 minute')",caseId,initial.equals("HIDDEN")?"SYSTEM_FLAG":"PRE_APPROVAL",item);
            jdbc.update("insert into moderation_actions(id,moderation_case_id,action_type,actor_admin_user_id,reason_code,metadata,created_at) values (?,?,?,?,'SPAM','{}'::jsonb,now()-interval '1 minute')",UUID.randomUUID(),caseId,initial.equals("HIDDEN")?"HIDE":"REJECT",admin);
            settings.saveAndFlush(SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",SettingValueType.ENUM,
                    new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),merchant,owner,Instant.now()));
            var beforeGallery=gallery(item);
            var beforeOffer=jdbc.queryForList("select to_jsonb(original)::text from location_variants original where variant_id=?",String.class,variant);
            var beforeVariant=jdbc.queryForMap("select * from variants where id=?",variant);
            var beforeAssets=jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner);
            String beforeFields=jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item);
            String seller="Bearer "+issuer.issue(owner,new DeviceMetadata("Test","Restore race seller")).accessToken().value();
            String administrator="Bearer "+issuer.issue(admin,new DeviceMetadata("Test","Restore race admin")).accessToken().value();
            String restoreKey=UUID.randomUUID().toString();
            String restoreRoute="/api/v1/admin/moderation-cases/"+caseId+"/restore-content";
            Callable<MockHttpServletResponse> edit=() -> mvc.perform(patch("/api/v1/merchants/"+merchant+"/catalog-items/"+item)
                    .header("Authorization",seller).header("If-Match","\"v0\"").contentType("application/json")
                    .content("{\"socialCaption\":\"New unapproved caption\",\"mediaIds\":[\""+replacement+"\",\""+firstMedia+"\"]}"))
                    .andReturn().getResponse();
            Callable<MockHttpServletResponse> restore=() -> mvc.perform(post(restoreRoute).header("Authorization",administrator)
                    .header("Idempotency-Key",restoreKey)).andReturn().getResponse();
            MockHttpServletResponse editResponse,restoreResponse;
            try (var blocker=jdbc.getDataSource().getConnection();var workers=Executors.newFixedThreadPool(2)) {
                blocker.setAutoCommit(false);
                try (var lock=blocker.prepareStatement("select id from catalog_items where id=? for no key update")) {
                    lock.setObject(1,item);
                    try (var rows=lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                var first=workers.submit(sellerFirst?edit:restore);
                var gate = new UpdateGate();
                try {
                    awaitItemUpdates(1);
                    var second=workers.submit(() -> {
                        UpdateGateInspector.current.set(gate);
                        try { return (sellerFirst?restore:edit).call(); }
                        finally { UpdateGateInspector.current.remove(); }
                    });
                    assertThat(gate.ready.await(10, TimeUnit.SECONDS)).as("second real transaction reached its item UPDATE").isTrue();
                    assertPublic(item,null,null);
                    blocker.rollback();
                    var firstResponse=first.get(15,TimeUnit.SECONDS);
                    gate.release.countDown();
                    var secondResponse=second.get(15,TimeUnit.SECONDS);
                    editResponse=sellerFirst?firstResponse:secondResponse;
                    restoreResponse=sellerFirst?secondResponse:firstResponse;
                } finally { gate.release.countDown(); blocker.rollback(); }
            }
            boolean edited=editResponse.getStatus()==204;
            assertThat(editResponse.getStatus()).as(editResponse.getContentAsString()).isIn(204,409);
            assertThat(restoreResponse.getStatus()).as(restoreResponse.getContentAsString()).isEqualTo(edited?409:200);
            assertThat((edited?restoreResponse:editResponse).getContentAsString()).contains("RESOURCE_VERSION_CONFLICT");
            assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,item)).isEqualTo(edited?"PENDING":"APPROVED");
            assertThat(jdbc.queryForObject("select social_caption from catalog_items where id=?",String.class,item)).isEqualTo(edited?"New unapproved caption":"Old reviewed caption");
            assertThat(jdbc.queryForObject("select version from catalog_items where id=?",Long.class,item)).isEqualTo(1L);
            if (edited) {
                assertThat(jdbc.queryForList("select media_asset_id from catalog_item_media where catalog_item_id=? order by sort_order",UUID.class,item)).containsExactly(replacement,firstMedia);
                assertThat(jdbc.queryForList("select sort_order from catalog_item_media where catalog_item_id=? order by sort_order",Integer.class,item)).containsExactly(0,1);
                assertThat(jdbc.queryForList("select is_primary from catalog_item_media where catalog_item_id=? order by sort_order",Boolean.class,item)).containsExactly(true,false);
                assertPublic(item,null,null);
                mvc.perform(post(restoreRoute).header("Authorization",administrator).header("Idempotency-Key",restoreKey))
                        .andExpect(status().isConflict());
            } else {
                assertThat(gallery(item)).isEqualTo(beforeGallery);
                assertPublic(item,"Old reviewed caption",List.of(firstMedia,secondMedia));
                var committed=jdbc.queryForMap("select * from catalog_items where id=?",item);
                mvc.perform(post(restoreRoute).header("Authorization",administrator).header("Idempotency-Key",restoreKey))
                        .andExpect(status().isOk());
                assertThat(jdbc.queryForMap("select * from catalog_items where id=?",item)).isEqualTo(committed);
            }
            assertThat(jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item)).isEqualTo(beforeFields);
            assertThat(jdbc.queryForList("select to_jsonb(original)::text from location_variants original where variant_id=?",String.class,variant)).isEqualTo(beforeOffer);
            assertThat(jdbc.queryForMap("select * from variants where id=?",variant)).isEqualTo(beforeVariant);
            assertThat(jdbc.queryForList("select to_jsonb(original)::text from media_assets original where owner_user_id=? order by id",String.class,owner)).isEqualTo(beforeAssets);
            assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",Long.class,item)).isEqualTo(edited?1L:0L);
            assertThat(jdbc.queryForObject("select case_status from moderation_cases where id=?",String.class,caseId)).isEqualTo("RESOLVED");
            for (String query:List.of("select count(*) from moderation_actions where moderation_case_id=? and action_type='RESTORE'",
                    "select count(*) from audit_logs where entity_id=? and action='MODERATION_RESTORE'",
                    "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED' and payload->>'action'='RESTORE'"))
                assertThat(jdbc.queryForObject(query,Long.class,caseId)).isEqualTo(edited?0L:1L);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id in (select id from moderation_cases where target_catalog_item_id=? and case_type='PRE_APPROVAL') and event_type='MODERATION_CASE_OPENED'",Long.class,item)).isEqualTo(edited?1L:0L);
            observed.add(initial+":"+(edited?"SELLER":"ADMIN"));
            System.out.println("Social edit/restore initial="+initial+" first="+(sellerFirst?"SELLER":"ADMIN")+" statuses="+editResponse.getStatus()+"/"+restoreResponse.getStatus()+" final="+(edited?"PENDING":"APPROVED")+" version=1");
        }
        assertThat(observed).as("both commit outcomes must actually execute for each fixed state")
                .containsExactlyInAnyOrder("HIDDEN:SELLER","HIDDEN:ADMIN","REJECTED:SELLER","REJECTED:ADMIN");
    }

    private void assertPublic(UUID item,String caption,List<UUID> media) throws Exception {
        var response=mvc.perform(get("/api/v1/catalog/social-previews").param("itemIds",item.toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        if (caption==null) {
            assertThat(root.has(item.toString())).isFalse();
            mvc.perform(get("/api/v1/catalog/items/"+item).param("lat","10.8").param("lng","106.7")).andExpect(status().isNotFound());
        } else {
            var entry=root.path(item.toString());
            assertThat(entry.path("socialCaption").asText()).isEqualTo(caption);
            var ids=new ArrayList<String>();
            for (var asset:entry.path("media")) ids.add(asset.path("id").asText());
            assertThat(ids).containsExactlyElementsOf(media.stream().map(UUID::toString).toList());
            mvc.perform(get("/api/v1/catalog/items/"+item).param("lat","10.8").param("lng","106.7")).andExpect(status().isOk());
        }
    }

    private List<String> gallery(UUID item) {
        return jdbc.queryForList("select to_jsonb(original)::text from catalog_item_media original where catalog_item_id=? order by sort_order",String.class,item);
    }

    private static final class UpdateGate {
        private final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    }

    public static final class UpdateGateInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        private static final ThreadLocal<UpdateGate> current = new ThreadLocal<>();

        @Override public String inspect(String sql) {
            var gate = current.get();
            if (gate != null && sql.toLowerCase(java.util.Locale.ROOT).contains("update catalog_items ")) {
                gate.ready.countDown();
                try {
                    if (!gate.release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Item update test gate timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Item update test gate interrupted", exception);
                }
            }
            return sql;
        }
    }

    private void awaitItemUpdates(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<java.util.Map<String,Object>> waiting=List.of();
        while (System.nanoTime()<deadline) {
            waiting=jdbc.queryForList("select pid,wait_event from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%update catalog_items%'");
            if (waiting.size()==expected) break;
            Thread.sleep(20);
        }
        assertThat(waiting).as("real versioned item UPDATE contention").hasSize(expected);
        System.out.println("Social edit/restore waiting="+expected+" events="+waiting.stream().map(row -> row.get("wait_event")).toList());
    }
}
