package vn.covie.social.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

@EnabledIfSystemProperty(named="covie.test.social-activity-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.activity-enabled=true","covie.auth.cookies.secure=false",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class SocialActivityBrowserIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired vn.covie.users.persistence.UserIdentityRepository identities;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;

    @Test void realNextRouteAndIssuedCookiesReconcileDuringAnOldPendingPage() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(web.resolve("app/(buyer)/me/activity/page.tsx")).exists();
        assertThat(script).exists();
        Files.createDirectories(output);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(first,"Browser buyer A",Instant.now()));
        users.saveAndFlush(UserEntity.active(second,"Browser buyer B",Instant.now()));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Browser shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"browser-"+merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Browser category',?,'PRODUCT','ACTIVE')",category,"browser-"+category);
        for (int index=0;index<22;index++) {
            UUID item=UUID.randomUUID();
            boolean belongsToFirst=index<21;
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',now(),now())",
                    item,merchant,category,(belongsToFirst?"A-private-":"B-private-")+index,"browser-"+item);
            jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind,created_at) values (?,?,'LIKE',now()-?*interval '1 second')",item,belongsToFirst?first:second,index);
            if (index==0) jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind) values (?,?,'SAVE')",item,first);
        }
        boolean realAuth=Boolean.getBoolean("covie.test.social-activity-real-auth");
        String firstEmail="",secondEmail="",password="";
        if (realAuth) {
            firstEmail="social-a-"+first+"@fixture.invalid";
            secondEmail="social-b-"+second+"@fixture.invalid";
            password="SocialFixture!"+UUID.randomUUID();
            identities.saveAndFlush(vn.covie.users.model.UserIdentityEntity.email(UUID.randomUUID(),first,firstEmail,passwords.encode(password),Instant.now()));
            identities.saveAndFlush(vn.covie.users.model.UserIdentityEntity.email(UUID.randomUUID(),second,secondEmail,passwords.encode(password),Instant.now()));
        }
        var before=realAuth?businessSnapshot():Map.<String,java.util.List<String>>of();
        String firstToken=realAuth?"":issuer.issue(first,new DeviceMetadata("Test","Browser A")).accessToken().value();
        String secondToken=realAuth?"":issuer.issue(second,new DeviceMetadata("Test","Browser B")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"firstToken",firstToken,"secondToken",secondToken,"output",output.toString(),
                "firstEmail",firstEmail,"secondEmail",secondEmail,"password",password)));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),
                "dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("COVIE_SOCIAL_V2_ACTIVITY_ENABLED","true");
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process webProcess=launcher.start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(180,TimeUnit.SECONDS)).as("browser run must finish within its bound").isTrue();
            assertThat(browser.exitValue()).as("inspect the redacted browser/Next logs").isZero();
            assertThat(output.resolve("evidence.json")).exists();
            if (realAuth) {
                assertThat(businessSnapshot()).isEqualTo(before);
                assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is not null",Long.class,first)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is null",Long.class,second)).isEqualTo(1L);
            }
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private Map<String,java.util.List<String>> businessSnapshot() {
        var result=new java.util.LinkedHashMap<String,java.util.List<String>>();
        for (String table:java.util.List.of("catalog_items","catalog_social_reactions","catalog_social_comments"))
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
