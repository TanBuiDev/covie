package vn.covie.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@EnabledIfSystemProperty(named="covie.test.catalog-image-spoof-storage",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=localhost","covie.social-v2.caption-authoring-enabled=true",
        "covie.outbox.max-events-per-poll=0","covie.retention.enabled=false"})
@Import(CatalogSocialBrowserIntegrationTest.StorageClients.class)
class CatalogImageSpoofStorageIntegrationTest extends PostgresIntegrationTest {
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        CatalogSocialBrowserIntegrationTest.storageProperties(registry);
    }
    @LocalServerPort int apiPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InitialSessionIssuer issuer;
    @Autowired S3Client s3;

    @Test void realPresignedCatalogImageWithMatchingMetadataButWrongMagicFailsClosed() throws Exception {
        String bucket=CatalogSocialBrowserIntegrationTest.BUCKET;
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        UUID owner=UUID.randomUUID(),merchant=UUID.randomUUID(),category=UUID.randomUUID(),item=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner,"Spoof image owner",Instant.now()));
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at) values (?,'Spoof fixture shop',?,'ACTIVE','APPROVED',now(),now())",merchant,"spoof-"+merchant);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at) values (?,?,?,'OWNER','ACTIVE',now(),now())",UUID.randomUUID(),merchant,owner);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Spoof fixture category',?,'PRODUCT','ACTIVE')",category,"spoof-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,social_caption,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Spoof fixture item',?,'Commerce unchanged','Caption unchanged','DRAFT','NOT_REQUIRED',now(),now())",item,merchant,category,"spoof-"+item);
        String token=issuer.issue(owner,new DeviceMetadata("Test","Catalog image spoof")).accessToken().value();
        var before=snapshot();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var http=HttpClient.newHttpClient();
        String api="http://localhost:"+apiPort;
        byte[] invalidPng=new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        var presign=http.send(HttpRequest.newBuilder(URI.create(api+"/api/v1/media/uploads"))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("purpose","CATALOG_IMAGE","contentType","image/png","fileSize",invalidPng.length)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(presign.statusCode()).isEqualTo(201);
        var upload=json.readTree(presign.body());
        UUID mediaId=UUID.fromString(upload.path("mediaId").asText());
        assertThat(upload.path("method").asText()).isEqualTo("PUT");
        var put=HttpRequest.newBuilder(URI.create(upload.path("uploadUrl").asText()));
        upload.path("requiredHeaders").fields().forEachRemaining(header -> put.header(header.getKey(),header.getValue().asText()));
        var stored=http.send(put.PUT(HttpRequest.BodyPublishers.ofByteArray(invalidPng)).build(),HttpResponse.BodyHandlers.discarding());
        assertThat(stored.statusCode()).isEqualTo(200);
        String key=jdbc.queryForObject("select storage_key from media_assets where id=?",String.class,mediaId);
        var head=s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        assertThat(head.contentLength()).isEqualTo((long)invalidPng.length);
        assertThat(head.contentType()).isEqualTo("image/png");
        assertThat(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()).isEqualTo(invalidPng);
        assertThat(jdbc.queryForObject("select media_status from media_assets where id=?",String.class,mediaId)).isEqualTo("PENDING_UPLOAD");
        String unchangedAsset=jdbc.queryForObject("select (to_jsonb(original)-'media_status')::text from media_assets original where id=?",String.class,mediaId);
        String completionKey=UUID.randomUUID().toString();
        var complete=HttpRequest.newBuilder(URI.create(api+"/api/v1/media/"+mediaId+"/complete"))
                .header("Authorization","Bearer "+token).header("Idempotency-Key",completionKey)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        for (int attempt=0;attempt<2;attempt++) {
            var rejected=http.send(complete,HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(422);
            assertThat(json.readTree(rejected.body()).path("error").path("code").asText()).isEqualTo("MEDIA_UPLOAD_VERIFICATION_FAILED");
            assertThat(rejected.body()).doesNotContain(key);
        }
        assertThat(jdbc.queryForObject("select media_status from media_assets where id=?",String.class,mediaId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select (to_jsonb(original)-'media_status')::text from media_assets original where id=?",String.class,mediaId)).isEqualTo(unchangedAsset);
        assertThatThrownBy(() -> s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()))
                .isInstanceOfSatisfying(S3Exception.class,error -> assertThat(error.statusCode()).isEqualTo(404));
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where actor_id=? and operation='media.complete' and status='COMPLETED' and response_status=422",Long.class,owner)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records",Long.class)).isEqualTo(1L);
        var attach=http.send(HttpRequest.newBuilder(URI.create(api+"/api/v1/merchants/"+merchant+"/catalog-items/"+item))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json").header("If-Match","\"v0\"")
                .method("PATCH",HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("mediaIds",List.of(mediaId))))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(attach.statusCode()).isEqualTo(422);
        assertThat(json.readTree(attach.body()).path("error").path("code").asText()).isEqualTo("MEDIA_NOT_ATTACHABLE");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from media_assets",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from media_assets where media_status='READY'",Long.class)).isZero();
    }

    private Map<String,List<String>> snapshot() {
        var result=new java.util.LinkedHashMap<String,List<String>>();
        for (String table:List.of("catalog_items","variants","location_variants","catalog_item_media","merchant_profiles","merchant_memberships","merchant_locations","categories","catalog_social_reactions","catalog_social_comments","moderation_cases","moderation_actions","audit_logs","outbox_events"))
            result.put(table,jdbc.queryForList("select to_jsonb(original)::text from "+table+" original order by 1",String.class));
        return result;
    }
}
