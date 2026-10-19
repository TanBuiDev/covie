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

@EnabledIfSystemProperty(named="covie.test.social-commerce-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.previews-enabled=true","covie.auth.cookies.secure=false",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
class SocialCommerceBrowserIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired vn.covie.catalog.application.CatalogSearchDocumentService search;

    @Test void socialDiscoveryCanCheckoutCompletePickupAndCreateVerifiedReview() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(script).exists();
        Files.createDirectories(output);
        UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID(),merchant=UUID.randomUUID(),location=UUID.randomUUID();
        UUID category=UUID.randomUUID(),item=UUID.randomUUID(),variant=UUID.randomUUID(),offer=UUID.randomUUID();
        users.saveAllAndFlush(List.of(UserEntity.active(buyer,"Commerce buyer",Instant.now()),UserEntity.active(seller,"Commerce seller",Instant.now())));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Commerce shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"commerce-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,seller);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Commerce counter','Fixture pickup address',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Commerce category',?,'PRODUCT','ACTIVE')",category,"commerce-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Social commerce product',?,'Unchanged commerce description','Social commerce caption','ACTIVE','APPROVED',now(),now())",item,merchant,category,"commerce-"+item);
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at) values (?,?,'Default',true,'ACTIVE',now(),now())",variant,item);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at) values (?,?,?,25000,'VND',true,'UNTRACKED',0,now(),now())",offer,location,variant);
        search.rebuild(item);
        var before=snapshot();
        String buyerToken=issuer.issue(buyer,new DeviceMetadata("Test","Commerce buyer")).accessToken().value();
        String sellerToken=issuer.issue(seller,new DeviceMetadata("Test","Commerce seller")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"buyerToken",buyerToken,"sellerToken",sellerToken,"merchantId",merchant.toString(),
                "itemId",item.toString(),"output",output.toString())));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),"dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED","true");
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        Process webProcess=launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile()).start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(300,TimeUnit.SECONDS)).isTrue();
            assertThat(browser.exitValue()).as("inspect redacted browser evidence").isZero();
            var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("evidence.json").toFile());
            assertThat(evidence.path("pageErrors").isArray()).isTrue();
            assertThat(evidence.path("pageErrors").size()).isZero();
            assertThat(snapshot()).isEqualTo(before);
            UUID order=UUID.fromString(evidence.path("orderId").asText());
            assertThat(jdbc.queryForObject("select count(*) from orders",Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForMap("select o.buyer_user_id,o.merchant_profile_id,o.merchant_location_id,o.status,o.total_amount,f.fulfillment_method,o.payment_method from orders o join product_fulfillments f on f.order_id=o.id where o.id=?",order))
                    .isEqualTo(Map.of("buyer_user_id",buyer,"merchant_profile_id",merchant,"merchant_location_id",location,"status","COMPLETED","total_amount",25000L,"fulfillment_method","PICKUP","payment_method","COD"));
            UUID orderItem=jdbc.queryForObject("select id from order_items where order_id=? and catalog_item_id=?",UUID.class,order,item);
            assertThat(jdbc.queryForObject("select count(*) from order_items",Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from product_fulfillments where order_id=?",Long.class,order)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from service_fulfillments",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from reviews where order_item_id=? and rating=5",Long.class,orderItem)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from reviews",Long.class)).isEqualTo(1L);
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private Map<String,List<String>> snapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        for (String table:List.of("catalog_items","variants","location_variants","catalog_item_media","media_assets","merchant_profiles","merchant_memberships","merchant_locations","categories","catalog_social_reactions","catalog_social_comments"))
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
