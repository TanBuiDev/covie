package vn.covie.admin;

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

@EnabledIfSystemProperty(named="covie.test.admin-catalog-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.admin-reads-enabled=true","covie.social-v2.admin-case-opening-enabled=true",
        "covie.auth.cookies.secure=false","covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class AdminCatalogBrowserIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;

    @Test void actualAdminBrowserReadsFiltersAndOpensCaseWithoutChangingCatalog() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(script).exists();
        Files.createDirectories(output);
        UUID actor=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(actor,"Browser admin",Instant.now()));
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())",UUID.randomUUID(),actor);
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Admin browser shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"admin-browser-"+merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Admin browser category',?,'PRODUCT','ACTIVE')",category,"admin-browser-"+category);
        UUID item=UUID.randomUUID();
        for (int position=1;position<=21;position++) {
            UUID id=position==1?item:UUID.randomUUID();
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,?,?,'Separate commerce description',?,'ACTIVE','APPROVED',now()-?*interval '1 second',now()-?*interval '1 second')",
                    id,merchant,category,"Admin browser item "+position,"admin-browser-"+id,
                    position==1?"<script>Literal admin caption</script>\nSecond line":null,position,position);
        }
        UUID media=UUID.randomUUID();
        jdbc.update("insert into media_assets(id,owner_user_id,storage_provider,storage_key,upload_purpose,mime_type,size_bytes,media_status,upload_expires_at,created_at) values (?,?,'S3',?,'CATALOG_IMAGE','image/png',128,'PENDING_UPLOAD',now()+interval '1 hour',now())",media,actor,"admin-browser-private-key-"+media);
        jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'IMAGE',0,true)",item,media);
        boolean accessDenial=Boolean.getBoolean("covie.test.admin-catalog-access-denial");
        boolean openingConflict=Boolean.getBoolean("covie.test.admin-catalog-opening-conflict");
        if (accessDenial) {
            for (int position=0;position<51;position++) {
                jdbc.update("insert into moderation_cases(id,case_type,target_type,target_catalog_item_id,reason_code,case_status,opened_at,closed_at) values (?,'SYSTEM_FLAG','CATALOG_ITEM',?,'BROWSER_HISTORY','CLOSED',now()-?*interval '1 second',now())",
                        UUID.randomUUID(),item,position);
            }
        }
        var before=snapshot();
        var stableCatalogBefore=openingConflict?stableCatalog(item):List.<String>of();
        String token=issuer.issue(actor,new DeviceMetadata("Test","Admin catalog browser")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"token",token,"merchantId",merchant.toString(),"itemId",item.toString(),"output",output.toString(),
                "apiBase","http://localhost:"+apiPort)));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),
                "dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("COVIE_SOCIAL_V2_ADMIN_READS_ENABLED","true");
        launcher.environment().put("COVIE_SOCIAL_V2_ADMIN_CASE_OPENING_ENABLED","true");
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process webProcess=launcher.start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(240,TimeUnit.SECONDS)).as("bounded Admin browser run").isTrue();
            assertThat(browser.exitValue()).as("inspect redacted browser/Next logs").isZero();
            assertThat(output.resolve("evidence.json")).exists();
            if (openingConflict) {
                assertThat(stableCatalog(item)).isEqualTo(stableCatalogBefore);
                var unchangedBefore=new java.util.LinkedHashMap<>(before);
                var unchangedAfter=new java.util.LinkedHashMap<>(snapshot());
                unchangedBefore.remove("catalog_items");
                unchangedAfter.remove("catalog_items");
                assertThat(unchangedAfter).isEqualTo(unchangedBefore);
                assertThat(jdbc.queryForMap("select moderation_status,version from catalog_items where id=?",item))
                        .isEqualTo(Map.of("moderation_status","HIDDEN","version",1L));
                var cases=jdbc.queryForList("select id from moderation_cases where target_catalog_item_id=?",UUID.class,item);
                assertThat(cases).hasSize(1);
                UUID caseId=cases.getFirst();
                assertThat(jdbc.queryForMap("select case_type,case_status,reason_code,description from moderation_cases where id=?",caseId))
                        .isEqualTo(Map.of("case_type","SYSTEM_FLAG","case_status","RESOLVED","reason_code","SPAM","description","Concurrent Admin observed content"));
                assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=?",Long.class,caseId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from moderation_actions where moderation_case_id=? and action_type='HIDE'",Long.class,caseId)).isEqualTo(1L);
                for (String action:List.of("ADMIN_MODERATION_CASE_OPENED","MODERATION_HIDE"))
                    assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action=?",Long.class,caseId,action)).isEqualTo(1L);
                for (String event:List.of("MODERATION_CASE_OPENED","MODERATION_ACTION_APPLIED"))
                    assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type=?",Long.class,caseId,event)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=?",Long.class,caseId)).isEqualTo(2L);
                assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,caseId)).isEqualTo(2L);
                return;
            }
            assertThat(snapshot()).isEqualTo(before);
            if (accessDenial) {
                assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is not null and revocation_reason='LOGOUT'",Long.class,actor)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is null",Long.class,actor)).isZero();
                return;
            }
            var cases=jdbc.queryForList("select id from moderation_cases where target_catalog_item_id=?",UUID.class,item);
            assertThat(cases).hasSize(1);
            UUID caseId=cases.getFirst();
            assertThat(jdbc.queryForMap("select case_type,case_status,reason_code,description from moderation_cases where id=?",caseId))
                    .isEqualTo(Map.of("case_type","SYSTEM_FLAG","case_status","OPEN","reason_code","SPAM","description","Browser observed content"));
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ADMIN_MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type='MODERATION_CASE_OPENED'",Long.class,caseId)).isEqualTo(1L);
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private Map<String,List<String>> snapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        var tables=Boolean.getBoolean("covie.test.admin-catalog-access-denial")
                ? List.of("catalog_items","catalog_item_media","media_assets","moderation_cases","moderation_actions","audit_logs","outbox_events")
                : List.of("catalog_items","catalog_item_media","media_assets");
        for (String table:tables)
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }

    private List<String> stableCatalog(UUID item) {
        return jdbc.queryForList("select (to_jsonb(original)-case when id=? then array['moderation_status','updated_at','version'] else array[]::text[] end)::text from catalog_items original order by id",String.class,item);
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
