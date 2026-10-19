package vn.covie.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@EnabledIfSystemProperty(named="covie.test.social-navigation-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.caption-authoring-enabled=true","covie.auth.cookies.secure=false",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class CatalogSocialNavigationBrowserIntegrationTest extends PostgresIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void publishedEditProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        if (Boolean.getBoolean("covie.test.social-published-edit-browser"))
            registry.add("covie.social-v2.admin-reads-enabled",() -> true);
    }
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired vn.covie.settings.persistence.SystemSettingRepository settings;

    @Test void nativeNextNavigationCanCancelOrDiscardDraftWithoutWritingIt() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(script).exists();
        Files.createDirectories(output);
        var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image,"png",output.resolve("pending.png").toFile());
        UUID owner=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID(),location=UUID.randomUUID(),item=UUID.randomUUID(),variant=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner,"Navigation owner",Instant.now()));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Navigation shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"navigation-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Navigation category',?,'PRODUCT','ACTIVE')",category,"navigation-"+category);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Navigation counter','1 Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Navigation item',?,'Commerce baseline','Persisted caption','DRAFT','NOT_REQUIRED',now(),now())",item,merchant,category,"navigation-"+item);
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",variant,item);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),location,variant);
        boolean feedbackScenario=Boolean.getBoolean("covie.test.social-seller-feedback-browser");
        boolean publishedEdit=Boolean.getBoolean("covie.test.social-published-edit-browser");
        String publishedAdminToken="";
        if (publishedEdit) {
            UUID admin=UUID.randomUUID();
            users.saveAndFlush(UserEntity.active(admin,"Published edit admin",Instant.now()));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            publishedAdminToken=issuer.issue(admin,new DeviceMetadata("Test","Published edit admin")).accessToken().value();
            jdbc.update("update catalog_items set listing_status='ACTIVE',moderation_status='APPROVED' where id=?",item);
            settings.saveAndFlush(vn.covie.settings.model.SystemSettingEntity.merchant(UUID.randomUUID(),"moderation.mode",
                    vn.covie.settings.model.SettingValueType.ENUM,new com.fasterxml.jackson.databind.node.TextNode("PRE_APPROVAL"),merchant,owner,Instant.now()));
        }
        String publicItemId="",reason="<script>window.SELLER_REASON_PROBE=1</script>";
        if (feedbackScenario) {
            UUID admin=UUID.randomUUID(),caseId=UUID.randomUUID(),publicItem=UUID.randomUUID(),publicVariant=UUID.randomUUID();
            users.saveAndFlush(UserEntity.active(admin,"Seller feedback admin",Instant.now()));
            jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),admin);
            jdbc.update("update catalog_items set listing_status='ACTIVE',moderation_status='APPROVED' where id=?",item);
            jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'OPEN_REASON_PRIVATE','OPEN',now())",caseId,item);
            String adminToken=issuer.issue(admin,new DeviceMetadata("Test","Seller feedback admin")).accessToken().value();
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+apiPort+"/api/v1/admin/moderation-cases/"+caseId+"/hide-content"))
                    .header("Authorization","Bearer "+adminToken).header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("reasonCode",reason,"note","ADMIN_NOTE_PRIVATE")))).build();
            assertThat(java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            publicItemId=publicItem.toString();
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Public feedback item',?,'Commerce baseline','Public persisted caption','ACTIVE','APPROVED',now(),now())",publicItem,merchant,category,"feedback-"+publicItem);
            jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",publicVariant,publicItem);
            jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",UUID.randomUUID(),location,publicVariant);
            jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,created_at) values (?,?,?,'<script>window.SELLER_COMMENT_PROBE=1</script>',now())",UUID.randomUUID(),publicItem,owner);
        }
        var before=snapshot();
        var unchangedItem=jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item);
        var socialBefore=publishedEdit?feedbackSnapshot():Map.<String,List<String>>of();
        var protectedBefore=new java.util.LinkedHashMap<String,List<String>>();
        if (publishedEdit) for (String table:List.of("merchant_profiles","merchant_memberships","merchant_locations","categories","media_assets","catalog_social_reactions"))
            protectedBefore.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        var feedbackBefore=feedbackScenario?feedbackSnapshot():Map.<String,List<String>>of();
        var offerBefore=jdbc.queryForList("select (to_jsonb(original)-'price'-'version'-'updated_at')::text from location_variants original order by 1",String.class);
        String token=issuer.issue(owner,new DeviceMetadata("Test","Navigation browser")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"token",token,"merchantId",merchant.toString(),"itemId",item.toString(),"output",output.toString(),
                "publicItemId",publicItemId,"reason",reason,"adminToken",publishedAdminToken)));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),
                "dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("COVIE_SOCIAL_V2_CAPTION_AUTHORING_ENABLED","true");
        if (publishedEdit) launcher.environment().put("COVIE_SOCIAL_V2_ADMIN_READS_ENABLED","true");
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process webProcess=launcher.start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(200,TimeUnit.SECONDS)).as("bounded native navigation browser run").isTrue();
            assertThat(browser.exitValue()).as("inspect redacted browser/Next logs").isZero();
            assertThat(output.resolve("evidence.json")).exists();
            String expectedOfferPrice=System.getProperty("covie.test.social-browser-offer-price");
            if (publishedEdit) {
                var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                for (String table:List.of("variants","location_variants","catalog_item_media"))
                    assertThat(snapshot().get(table)).as(table).isEqualTo(before.get(table));
                assertThat(jdbc.queryForObject("select (to_jsonb(original)-'social_caption'-'moderation_status'-'updated_at'-'version')::text from catalog_items original where id=?",String.class,item)).isEqualTo(unchangedItem);
                assertThat(jdbc.queryForObject("select social_caption from catalog_items where id=?",String.class,item)).isEqualTo(evidence.path("caption").asText());
                assertThat(jdbc.queryForObject("select moderation_status from catalog_items where id=?",String.class,item)).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("select version from catalog_items where id=?",Long.class,item)).isEqualTo(1L);
                UUID caseId=UUID.fromString(evidence.path("caseId").asText());
                assertThat(jdbc.queryForObject("select count(*) from moderation_cases where id=? and target_catalog_item_id=? and case_type='PRE_APPROVAL' and case_status='OPEN'",Long.class,caseId,item)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from moderation_cases",Long.class)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events",Long.class)).isEqualTo(1L);
                for (String table:List.of("catalog_social_comments","moderation_actions","audit_logs"))
                    assertThat(feedbackSnapshot().get(table)).as(table).isEqualTo(socialBefore.get(table));
                for (String table:protectedBefore.keySet())
                    assertThat(jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class)).as(table).isEqualTo(protectedBefore.get(table));
            } else if (expectedOfferPrice==null) {
                assertThat(snapshot()).isEqualTo(before);
            } else {
                var after=snapshot();
                for (String table:List.of("catalog_items","variants","catalog_item_media"))
                    assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
                assertThat(jdbc.queryForList("select (to_jsonb(original)-'price'-'version'-'updated_at')::text from location_variants original order by 1",String.class)).isEqualTo(offerBefore);
                assertThat(jdbc.queryForObject("select price from location_variants where variant_id=?",java.math.BigDecimal.class,variant)).isEqualByComparingTo(expectedOfferPrice);
                assertThat(jdbc.queryForObject("select version from location_variants where variant_id=?",Long.class,variant)).isEqualTo(1L);
            }
            assertThat(jdbc.queryForObject("select count(*) from media_assets where owner_user_id=?",Long.class,owner)).isZero();
            if (feedbackScenario) assertThat(feedbackSnapshot()).isEqualTo(feedbackBefore);
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private Map<String,List<String>> snapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        for (String table : List.of("catalog_items","variants","location_variants","catalog_item_media"))
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }

    private Map<String,List<String>> feedbackSnapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        for (String table:List.of("catalog_social_comments","moderation_cases","moderation_actions","audit_logs","outbox_events"))
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }

    private void stop(Process process) throws Exception {
        var children=process.toHandle().descendants().toList();
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        process.waitFor(5,TimeUnit.SECONDS);
        for (var child : children) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
    }
}
