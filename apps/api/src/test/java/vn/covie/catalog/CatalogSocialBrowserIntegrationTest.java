package vn.covie.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@EnabledIfSystemProperty(named="covie.test.social-seller-browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.caption-authoring-enabled=true","covie.auth.cookies.secure=false",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
@Import(CatalogSocialBrowserIntegrationTest.StorageClients.class)
class CatalogSocialBrowserIntegrationTest extends PostgresIntegrationTest {
    static final String BUCKET="social-seller-browser";
    static final GenericContainer<?> MINIO=new GenericContainer<>(DockerImageName.parse(System.getProperty("covie.test.minio-image",
            "quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")))
            .withEnv("MINIO_ROOT_USER","test-access-key").withEnv("MINIO_ROOT_PASSWORD","test-secret-key")
            .withEnv("MINIO_API_CORS_ALLOW_ORIGIN","http://localhost:3000")
            .withCommand("server","/data").withExposedPorts(9000);
    static { MINIO.start(); }
    static URI endpoint() { return URI.create("http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000)); }
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("covie.storage.endpoint",() -> endpoint().toString());
        registry.add("covie.storage.public-base-url",() -> endpoint()+"/"+BUCKET);
        registry.add("covie.storage.bucket",() -> BUCKET);
        registry.add("covie.storage.force-path-style",() -> true);
        if (Boolean.getBoolean("covie.test.social-seller-publish-journey")) registry.add("covie.social-v2.previews-enabled",() -> true);
    }
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired S3Client s3;

    @Test void createsAndEditsCaptionAndOrderedImagesThroughRealNextApiAndStorage() throws Exception {
        Path web=Path.of(System.getProperty("covie.test.social-browser-web")).toAbsolutePath();
        Path script=Path.of(System.getProperty("covie.test.social-browser-script")).toAbsolutePath();
        Path output=Path.of(System.getProperty("covie.test.social-browser-output")).toAbsolutePath();
        assertThat(web.resolve("app/(seller)/seller/[merchantId]/catalog/new/page.tsx")).exists();
        assertThat(script).exists();
        Files.createDirectories(output);
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        s3.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(BUCKET).policy("""
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}]}
                """.formatted(BUCKET)).build());
        for (String name : java.util.List.of("first.png","second.png","third.png")) {
            var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);
            image.setRGB(0,0,name.hashCode());
            javax.imageio.ImageIO.write(image,"png",output.resolve(name).toFile());
        }
        UUID owner=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID(),location=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner,"Gallery owner",Instant.now()));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Gallery shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"gallery-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Gallery category',?,'PRODUCT','ACTIVE')",category,"gallery-"+category);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,currency_code,created_at,updated_at) values (?,?,'Gallery counter','1 Fixture',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,'VND',now(),now())",location,merchant);
        boolean publishJourney=Boolean.getBoolean("covie.test.social-seller-publish-journey");
        if (publishJourney) {
            jdbc.update("update categories set catalog_type='BOTH' where id=?",category);
            jdbc.update("update merchant_locations set service_enabled=true where id=?",location);
        }
        var before=new java.util.LinkedHashMap<String,java.util.List<String>>();
        if (publishJourney) for (String table:java.util.List.of("merchant_profiles","merchant_memberships","merchant_locations","categories"))
            before.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        String token=issuer.issue(owner,new DeviceMetadata("Test","Seller browser")).accessToken().value();
        int webPort;
        try (var socket=new ServerSocket(0)) { webPort=socket.getLocalPort(); }
        Path manifest=output.resolve("fixture.json");
        Files.writeString(manifest,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "webPort",webPort,"token",token,"merchantId",merchant.toString(),"output",output.toString(),"storageOrigin",endpoint().toString())));
        String node=System.getProperty("covie.test.node","node");
        var launcher=new ProcessBuilder(node,web.resolve("node_modules/next/dist/bin/next").toString(),
                "dev","--webpack","-p",Integer.toString(webPort),"-H","localhost").directory(web.toFile());
        launcher.environment().put("API_INTERNAL_BASE_URL","http://localhost:"+apiPort);
        launcher.environment().put("COVIE_PUBLIC_URL","http://localhost:3000");
        launcher.environment().put("COVIE_SOCIAL_V2_CAPTION_AUTHORING_ENABLED","true");
        if (publishJourney) launcher.environment().put("NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED","true");
        launcher.environment().put("NEXT_TELEMETRY_DISABLED","1");
        launcher.redirectErrorStream(true).redirectOutput(output.resolve("next.log").toFile());
        Process webProcess=launcher.start();
        Process browser=null;
        try {
            browser=new ProcessBuilder(node,script.toString(),manifest.toString()).directory(web.toFile())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            assertThat(browser.waitFor(publishJourney?300:200,TimeUnit.SECONDS)).as("bounded Seller browser run").isTrue();
            assertThat(browser.exitValue()).as("inspect redacted browser/Next logs").isZero();
            var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(output.resolve("evidence.json")));
            if (publishJourney) {
                assertThat(evidence.path("pageErrors").isArray()).isTrue();
                assertThat(evidence.path("pageErrors").size()).isZero();
                assertThat(evidence.path("items").size()).isEqualTo(3);
                assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant)).isEqualTo(3L);
                for (var row:evidence.path("items")) {
                    UUID id=UUID.fromString(row.path("id").asText());
                    assertThat(jdbc.queryForMap("select item_type,listing_kind,name,description,social_caption,listing_status,moderation_status,version from catalog_items where id=?",id))
                            .isEqualTo(Map.of("item_type",row.path("type").asText(),"listing_kind",row.path("listingKind").asText(),
                                    "name",row.path("name").asText(),"description","Commercial "+row.path("label").asText(),
                                    "social_caption",row.path("caption").asText(),"listing_status","ACTIVE","moderation_status","NOT_REQUIRED",
                                    "version",row.path("createVersion").asLong()+1));
                    assertThat(jdbc.queryForObject("select count(*) from variants where catalog_item_id=? and is_default=true",Long.class,id)).isEqualTo(1L);
                    assertThat(jdbc.queryForMap("select offer.merchant_location_id,offer.price,offer.currency_code,offer.is_available from location_variants offer join variants variant on variant.id=offer.variant_id where variant.catalog_item_id=?",id))
                            .isEqualTo(Map.of("merchant_location_id",location,"price",25000L,"currency_code","VND","is_available",true));
                    var mediaRow=jdbc.queryForMap("select asset.id,asset.storage_key,asset.owner_user_id,asset.upload_purpose,asset.media_status,link.sort_order,link.is_primary from catalog_item_media link join media_assets asset on asset.id=link.media_asset_id where link.catalog_item_id=?",id);
                    assertThat(mediaRow).containsEntry("owner_user_id",owner).containsEntry("upload_purpose","CATALOG_IMAGE")
                            .containsEntry("media_status","READY").containsEntry("sort_order",0).containsEntry("is_primary",true);
                    assertThat(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key((String)mediaRow.get("storage_key")).build()).asByteArray())
                            .isEqualTo(Files.readAllBytes(output.resolve(row.path("file").asText())));
                    for (String event:java.util.List.of("CATALOG_ITEM_CREATED","CATALOG_ITEM_PUBLISHED"))
                        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and event_type=?",Long.class,id,event)).isEqualTo(1L);
                }
                assertThat(jdbc.queryForObject("select count(*) from media_assets where owner_user_id=?",Long.class,owner)).isEqualTo(3L);
                assertThat(jdbc.queryForObject("select count(*) from idempotency_records where operation=?",Long.class,"CATALOG_ITEM_CREATE:"+merchant)).isEqualTo(3L);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where actor_user_id=? and action='MEDIA_UPLOAD_COMPLETED'",Long.class,owner)).isEqualTo(3L);
                assertThat(jdbc.queryForObject("select count(*) from moderation_cases where target_catalog_item_id in (select id from catalog_items where merchant_profile_id=?)",Long.class,merchant)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from catalog_social_reactions",Long.class)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from catalog_social_comments",Long.class)).isZero();
                for (var entry:before.entrySet()) assertThat(jdbc.queryForList("select to_jsonb(original)::text from "+entry.getKey()+" original order by 1",String.class))
                        .as(entry.getKey()).isEqualTo(entry.getValue());
                return;
            }
            UUID itemId=UUID.fromString(evidence.path("itemId").asText());
            assertThat(jdbc.queryForObject("select count(*) from catalog_items where merchant_profile_id=?",Long.class,merchant)).isEqualTo(1);
            assertThat(jdbc.queryForMap("select description,social_caption,listing_status from catalog_items where id=?",itemId))
                    .containsEntry("description","Commercial baseline").containsEntry("social_caption",null).containsEntry("listing_status","DRAFT");
            assertThat(jdbc.queryForObject("select price from location_variants offer join variants variant on variant.id=offer.variant_id where variant.catalog_item_id=?",Long.class,itemId)).isEqualTo(25000);
            var assets=jdbc.queryForList("select asset.storage_key from catalog_item_media link join media_assets asset on asset.id=link.media_asset_id where link.catalog_item_id=? and asset.media_status='READY' order by link.sort_order",String.class,itemId);
            assertThat(assets).hasSize(3).doesNotHaveDuplicates();
            String[] expectedFiles={"third.png","second.png","first.png"};
            for (int index=0;index<assets.size();index++) assertThat(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(assets.get(index)).build()).asByteArray())
                    .isEqualTo(Files.readAllBytes(output.resolve(expectedFiles[index])));
        } finally {
            if (browser!=null && browser.isAlive()) stop(browser);
            stop(webProcess);
            Files.deleteIfExists(manifest);
        }
    }

    private void stop(Process process) throws Exception {
        var children=process.toHandle().descendants().toList();
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        process.waitFor(5,TimeUnit.SECONDS);
        for (var child : children) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
    }

    @org.springframework.boot.test.context.TestConfiguration static class StorageClients {
        static StaticCredentialsProvider credentials() { return StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key","test-secret-key")); }
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        S3Client isolatedS3Client() { return S3Client.builder().endpointOverride(endpoint()).region(Region.US_EAST_1)
                .credentialsProvider(credentials()).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build(); }
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        S3Presigner isolatedS3Presigner() { return S3Presigner.builder().endpointOverride(endpoint()).region(Region.US_EAST_1)
                .credentialsProvider(credentials()).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build(); }
    }
}
