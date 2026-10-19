package vn.covie.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@EnabledIfSystemProperty(named="covie.test.social-feed-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.previews-enabled=true","covie.auth.cookies.secure=false",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
@Import(CatalogSocialBrowserIntegrationTest.StorageClients.class)
class SocialFeedBrowserIntegrationTest extends PostgresIntegrationTest {
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        CatalogSocialBrowserIntegrationTest.storageProperties(registry);
        registry.add("covie.social-v2.activity-enabled",() -> Boolean.getBoolean("covie.test.social-feed-buyer-journey"));
        if (Boolean.getBoolean("covie.test.social-feed-admin-journey")) {
            registry.add("covie.social-v2.admin-reads-enabled",() -> true);
            registry.add("covie.social-v2.admin-case-opening-enabled",() -> true);
        }
    }
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired S3Client s3;
    @Autowired vn.covie.catalog.application.CatalogSearchDocumentService searchDocuments;

    @Test void realSearchSocialCardsUseAuthoritativePreviewsAndStorageWithoutWrites() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(script).exists();
        Files.createDirectories(output);
        String bucket=CatalogSocialBrowserIntegrationTest.BUCKET;
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        s3.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy("""
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}]}
                """.formatted(bucket)).build());
        UUID actor=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID(),location=UUID.randomUUID(),item=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(actor,"Social browser buyer",Instant.now()));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Social browser shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"social-browser-"+merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Social browser category',?,'PRODUCT','ACTIVE')",category,"social-browser-"+category);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Social browser counter','1 Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        String caption="<script>Literal Social caption</script>\n"+"Dòng caption dài để kiểm tra hiển thị nguyên văn và mở rộng nội dung.\n".repeat(5);
        for (int position=0;position<2;position++) {
            UUID id=position==0?item:UUID.randomUUID(),variant=UUID.randomUUID();
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,?,?,'Commercial baseline not a Social caption',?,'ACTIVE','APPROVED',now(),now())",
                    id,merchant,category,position==0?"Social browser product":"Social empty product","social-browser-"+id,position==0?caption:null);
            jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",variant,id);
            jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),location,variant);
        }
        var expectedMedia=new java.util.ArrayList<String>();
        for (int position=0;position<9;position++) {
            UUID media=UUID.randomUUID();
            String key="social-feed-"+media+".png";
            var image=new java.awt.image.BufferedImage(16,12,java.awt.image.BufferedImage.TYPE_INT_RGB);
            image.setRGB(0,0,position*10000);
            var bytes=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image,"png",bytes);
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),RequestBody.fromBytes(bytes.toByteArray()));
            if (position==5) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
                assertThatThrownBy(() -> s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()))
                        .isInstanceOfSatisfying(S3Exception.class,error -> assertThat(error.statusCode()).isEqualTo(404));
            }
            jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,verified_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',?,?,now()+interval '1 hour',now(),now())",
                    media,actor,key,bytes.size(),position<8?"READY":"PENDING_UPLOAD");
            jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',?,?)",item,media,position,position==0);
            if (position<6) expectedMedia.add(media.toString());
        }
        var ineligibleItems=new java.util.ArrayList<Map<String,String>>();
        if (Boolean.getBoolean("covie.test.social-feed-ineligible")) {
            for (String state:List.of("MERCHANT_HIDDEN","MERCHANT_SUSPENDED","ITEM_DELETED")) {
                UUID blockedMerchant=UUID.randomUUID(),blockedLocation=UUID.randomUUID(),blockedItem=UUID.randomUUID(),blockedVariant=UUID.randomUUID();
                jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Ineligible shop',?,?,?,now(),now())",
                        blockedMerchant,"ineligible-"+blockedMerchant,state.equals("MERCHANT_SUSPENDED")?"SUSPENDED":"ACTIVE",state.equals("MERCHANT_HIDDEN")?"HIDDEN":"APPROVED");
                jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Ineligible counter','1 Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",blockedLocation,blockedMerchant);
                jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at,deleted_at) values (?,?,'PRODUCT','STANDARD',?,?,?,'Ineligible commerce',?,?,'APPROVED',now(),now(),?)",
                        blockedItem,blockedMerchant,category,"Ineligible "+state,"ineligible-"+blockedItem,"Private caption "+state,state.equals("ITEM_DELETED")?"DELETED":"ACTIVE",state.equals("ITEM_DELETED")?java.sql.Timestamp.from(Instant.now()):null);
                jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",blockedVariant,blockedItem);
                jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),blockedLocation,blockedVariant);
                jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,created_at) values (?,?,?, ?,now())",UUID.randomUUID(),blockedItem,actor,"Private comment "+state);
                ineligibleItems.add(Map.of("state",state,"id",blockedItem.toString()));
            }
        }
        boolean adsJourney=Boolean.getBoolean("covie.test.social-feed-ads");
        boolean searchJourney=Boolean.getBoolean("covie.test.social-feed-search-filters");
        if (searchJourney) jdbc.update("update location_variants set price=60000 where variant_id in (select v.id from variants v join catalog_items i on i.id=v.catalog_item_id where i.merchant_profile_id=? and i.id<>?)",merchant,item);
        if (adsJourney || searchJourney) for (UUID indexedItem:jdbc.queryForList("select id from catalog_items where merchant_profile_id=?",UUID.class,merchant))
            searchDocuments.rebuild(indexedItem);
        if (adsJourney) for (String placement:List.of("HOME_HERO","IN_FEED")) {
            var payload=new java.util.LinkedHashMap<String,Object>();
            payload.put("title","Fixture "+placement); payload.put("body","Approved fixture advertisement");
            payload.put("ctaLabel","Xem fixture"); payload.put("targetUrl","/search?type=PRODUCT&q=Social&sort=NEWEST&view=social");
            payload.put("creativeType","CSS"); payload.put("template","HERO"); payload.put("placement",placement); payload.put("device","ALL");
            payload.put("startsAt",Instant.now().minusSeconds(60).toString()); payload.put("endsAt",Instant.now().plusSeconds(3600).toString());
            payload.put("enabled",true); payload.put("priority",1000);
            jdbc.update("insert into ad_campaigns(id,merchant_id,owner_user_id,payload,placement,device,status,enabled,priority,starts_at,ends_at,created_at,updated_at) values (?,?,?,?::jsonb,?,'ALL','APPROVED',true,1000,now()-interval '1 minute',now()+interval '1 hour',now(),now())",
                    UUID.randomUUID(),merchant,actor,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload),placement);
        }
        var beforeCampaigns=adsJourney?jdbc.queryForList("select to_jsonb(original)::text from ad_campaigns original order by 1",String.class):List.<String>of();
        var before=snapshot();
        boolean hideScenario=Boolean.getBoolean("covie.test.social-feed-hide");
        boolean adminJourney=Boolean.getBoolean("covie.test.social-feed-admin-journey");
        String adminToken="";
        UUID admin=UUID.randomUUID();
        if (hideScenario || adminJourney) {
            users.saveAndFlush(UserEntity.active(admin,"Social hide browser admin",Instant.now()));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            adminToken=issuer.issue(admin,new DeviceMetadata("Test","Social hide browser admin")).accessToken().value();
        }
        String beforeItemFields=jdbc.queryForObject("select (to_jsonb(original)-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item);
        var beforeOtherItems=jdbc.queryForList("select to_jsonb(original)::text from catalog_items original where id<>? order by id",String.class,item);
        boolean guestScenario=Boolean.getBoolean("covie.test.social-feed-guest");
        boolean buyerJourney=Boolean.getBoolean("covie.test.social-feed-buyer-journey");
        if (buyerJourney) {
            assertThat(jdbc.queryForObject("select count(*) from catalog_social_reactions",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from catalog_social_comments",Long.class)).isZero();
        }
        String token=guestScenario?"":issuer.issue(actor,new DeviceMetadata("Test","Social feed browser")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"token",token,"itemId",item.toString(),"output",output.toString(),
                "caption",caption,"expectedMedia",expectedMedia,"storageOrigin",CatalogSocialBrowserIntegrationTest.endpoint().toString(),
                "apiBase","http://localhost:"+apiPort,"adminToken",adminToken,"ineligibleItems",ineligibleItems)));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),
                "dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED","true");
        if (buyerJourney) launcher.environment().put("COVIE_SOCIAL_V2_ACTIVITY_ENABLED","true");
        if (adminJourney) {
            launcher.environment().put("COVIE_SOCIAL_V2_ADMIN_READS_ENABLED","true");
            launcher.environment().put("COVIE_SOCIAL_V2_ADMIN_CASE_OPENING_ENABLED","true");
        }
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process webProcess=launcher.start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(240,TimeUnit.SECONDS)).as("bounded Social feed browser run").isTrue();
            assertThat(browser.exitValue()).as("inspect redacted browser/Next logs").isZero();
            assertThat(output.resolve("evidence.json")).exists();
            if (searchJourney) {
                var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                assertThat(evidence.path("stages").size()).isEqualTo(6);
            }
            if (adsJourney) {
                var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                assertThat(jdbc.queryForList("select to_jsonb(original)::text from ad_campaigns original order by 1",String.class)).isEqualTo(beforeCampaigns);
                assertThat(jdbc.queryForObject("select count(*) from ad_deliveries",Long.class)).isEqualTo((long)evidence.path("deliveries").size());
                var eventIds=new java.util.ArrayList<String>();
                for (var event:evidence.path("events")) {
                    eventIds.add(event.path("eventId").asText());
                    assertThat(jdbc.queryForObject("select count(*) from ad_events e join ad_deliveries d on d.id::text=e.delivery_key where e.event_id=? and e.campaign_id=? and e.event_type=? and d.campaign_id=e.campaign_id and d.campaign_version=0",Long.class,
                            UUID.fromString(event.path("eventId").asText()),UUID.fromString(event.path("campaignId").asText()),event.path("type").asText())).isEqualTo(1L);
                }
                assertThat(jdbc.queryForList("select event_id::text from ad_events order by event_id::text",String.class)).containsExactlyInAnyOrderElementsOf(eventIds);
                for (String placement:List.of("HOME_HERO","IN_FEED")) {
                    assertThat(jdbc.queryForObject("select count(*) from ad_events e join ad_campaigns c on c.id=e.campaign_id where c.placement=? and e.event_type='CLICK'",Long.class,placement)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("select count(*) from ad_events e join ad_campaigns c on c.id=e.campaign_id where c.placement=? and e.event_type='IMPRESSION'",Long.class,placement)).isGreaterThanOrEqualTo(1L);
                }
            }
            if (Boolean.getBoolean("covie.test.social-feed-ineligible")) {
                var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                assertThat(evidence.path("blocked").size()).isEqualTo(3);
                assertThat(jdbc.queryForObject("select count(*) from catalog_social_comments",Long.class)).isEqualTo(3L);
            }
            if (adminJourney) {
                var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                var after=snapshot();
                for (String table:List.of("variants","location_variants","catalog_item_media","media_assets","catalog_social_reactions","catalog_social_comments"))
                    assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
                assertThat(jdbc.queryForList("select to_jsonb(original)::text from catalog_items original where id<>? order by id",String.class,item)).isEqualTo(beforeOtherItems);
                assertThat(jdbc.queryForObject("select (to_jsonb(original)-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item)).isEqualTo(beforeItemFields);
                assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,item)).isEqualTo("APPROVED");
                assertThat(jdbc.queryForObject("select version from catalog_items where id=?",Long.class,item)).isEqualTo(2L);
                UUID caseId=UUID.fromString(evidence.path("caseId").asText());
                assertThat(jdbc.queryForObject("select count(*) from moderation_cases where id=? and target_catalog_item_id=? and case_type='SYSTEM_FLAG' and case_status='RESOLVED'",Long.class,caseId,item)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isEqualTo(1L);
                for (String action:List.of("HIDE","RESTORE")) {
                    assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=? and actor_admin_user_id=? and action_type=?",Long.class,caseId,admin,action)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action=?",Long.class,caseId,"MODERATION_"+action)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED' and payload->>'action'=?",Long.class,caseId,action)).isEqualTo(1L);
                }
                assertThat(jdbc.queryForObject("select count(*) from moderation_actions",Long.class)).isEqualTo(2L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs",Long.class)).isEqualTo(3L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events",Long.class)).isEqualTo(3L);
            } else if (hideScenario) {
                var after=snapshot();
                for (String table:List.of("variants","location_variants","catalog_item_media","media_assets"))
                    assertThat(after.get(table)).isEqualTo(before.get(table));
                assertThat(jdbc.queryForList("select to_jsonb(original)::text from catalog_items original where id<>? order by id",String.class,item)).isEqualTo(beforeOtherItems);
                assertThat(jdbc.queryForObject("select (to_jsonb(original)-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item)).isEqualTo(beforeItemFields);
                assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,item)).isEqualTo("HIDDEN");
                assertThat(jdbc.queryForObject("select version from catalog_items where id=?",Long.class,item)).isEqualTo(1L);
                var caseRow=jdbc.queryForMap("select id,case_type,case_status from moderation_cases where target_catalog_item_id=?",item);
                assertThat(caseRow.get("case_type")).isEqualTo("USER_REPORT");
                assertThat(caseRow.get("case_status")).isEqualTo("RESOLVED");
                UUID caseId=(UUID)caseRow.get("id");
                for (String query:List.of("select count(*) from moderation_actions where moderation_case_id=? and action_type='HIDE'",
                        "select count(*) from audit_logs where entity_id=? and action='MODERATION_HIDE'",
                        "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_ACTION_APPLIED' and payload->>'action'='HIDE'",
                        "select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'"))
                    assertThat(jdbc.queryForObject(query,Long.class,caseId)).isEqualTo(1L);
            } else {
                assertThat(snapshot()).isEqualTo(before);
            }
            boolean reactionOverlap=Boolean.getBoolean("covie.test.social-feed-reaction-overlap");
            assertThat(jdbc.queryForObject("select count(*) from catalog_social_reactions",Long.class)).isEqualTo(buyerJourney?2L:reactionOverlap?1L:0L);
            if (reactionOverlap) assertThat(jdbc.queryForObject("select count(*) from catalog_social_reactions where catalog_item_id=? and user_id=? and kind='LIKE'",Long.class,item,actor)).isEqualTo(1L);
            if (guestScenario) assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Long.class,actor)).isZero();
            if (buyerJourney) {
                assertThat(jdbc.queryForList("select kind from catalog_social_reactions where catalog_item_id=? and user_id=? order by kind",String.class,item,actor))
                        .containsExactly("LIKE","SAVE");
                assertThat(jdbc.queryForObject("select count(*) from catalog_social_comments",Long.class)).isEqualTo(1L);
                assertThat(jdbc.queryForMap("select catalog_item_id,author_user_id,body,status from catalog_social_comments"))
                        .isEqualTo(Map.of("catalog_item_id",item,"author_user_id",actor,"body","<script>Buyer literal comment</script>\nActivity chain","status","ACTIVE"));
                var browserEvidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(output.resolve("evidence.json")));
                assertThat(jdbc.queryForObject("select id from catalog_social_comments",UUID.class))
                        .isEqualTo(UUID.fromString(browserEvidence.path("createdCommentId").asText()));
                assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is null",Long.class,actor)).isEqualTo(1L);
            }
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private Map<String,List<String>> snapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        var tables=Boolean.getBoolean("covie.test.social-feed-ineligible")
                ? List.of("catalog_items","variants","location_variants","catalog_item_media","media_assets",
                        "catalog_social_reactions","catalog_social_comments","moderation_cases","moderation_actions","audit_logs","outbox_events",
                        "merchant_profiles","merchant_locations","categories")
                : Boolean.getBoolean("covie.test.social-feed-guest")
                ? List.of("catalog_items","variants","location_variants","catalog_item_media","media_assets",
                        "catalog_social_reactions","catalog_social_comments","moderation_cases","moderation_actions","audit_logs","outbox_events")
                : Boolean.getBoolean("covie.test.social-feed-buyer-journey")
                    ? List.of("catalog_items","variants","location_variants","catalog_item_media","media_assets",
                            "moderation_cases","moderation_actions","audit_logs","outbox_events")
                    : List.of("catalog_items","variants","location_variants","catalog_item_media","media_assets");
        for (String table:tables)
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }

    private void stop(Process process) throws Exception {
        var children=process.toHandle().descendants().toList();
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        process.waitFor(5,TimeUnit.SECONDS);
        for (var child:children) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
    }
}
