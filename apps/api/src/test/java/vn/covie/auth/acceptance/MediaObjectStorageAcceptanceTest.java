package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import vn.covie.media.model.MediaStatus;

@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(MediaObjectStorageAcceptanceTest.TestStorageClients.class)
class MediaObjectStorageAcceptanceTest extends AuthAcceptanceSupport {
    static final String BUCKET="media-api-test";
    static final GenericContainer<?> MINIO=new GenericContainer<>(DockerImageName.parse(System.getProperty("covie.test.minio-image",
            "quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")))
            .withEnv("MINIO_ROOT_USER","test-access-key").withEnv("MINIO_ROOT_PASSWORD","test-secret-key")
            .withCommand("server","/data").withExposedPorts(9000);
    static { MINIO.start(); }
    static URI endpoint() { return URI.create("http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000)); }
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("covie.storage.endpoint",()->endpoint().toString());
        registry.add("covie.storage.public-base-url",()->endpoint()+"/"+BUCKET);
        registry.add("covie.storage.bucket",()->BUCKET);
        registry.add("covie.storage.force-path-style",()->true);
    }
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired S3Client s3;
    @Autowired vn.covie.media.persistence.MediaAssetRepository media;
    @BeforeEach void prepareBucket() {
        try { s3.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()); }
        catch(NoSuchBucketException missing) { s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()); }
        catch(S3Exception missing) {
            if(missing.statusCode()!=404) throw missing;
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test void signedHttpUploadCompletesReadyAndCannotBeOverwrittenOrCompletedByAnotherUser() throws Exception {
        var owner=activeUser("Real upload owner"); var other=activeUser("Real upload outsider");
        String bearer="Bearer "+issuer.issue(owner.getId(),device()).accessToken().value();
        String foreign="Bearer "+issuer.issue(other.getId(),device()).accessToken().value();
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADElEQVR42mP4z8DwHwAFAP+JmT0dAAAAAElFTkSuQmCC");
        var upload=createUpload(bearer,png.length);
        assertThat(put(upload,png)).isEqualTo(200);
        UUID id=UUID.fromString(upload.path("mediaId").asText()); String path="/api/v1/media/"+id+"/complete";
        mvc.perform(post(path).header("Authorization",foreign).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        String key=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path).header("Authorization",bearer).header("Idempotency-Key",key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mediaId").value(id.toString()));
        var asset=media.findById(id).orElseThrow();
        assertThat(asset.getMediaStatus()).isEqualTo(MediaStatus.READY);
        assertThat(s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(asset.getStorageKey()).build()).contentLength()).isEqualTo(png.length);
        assertThat(put(upload,png)).isEqualTo(412);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='MEDIA_UPLOAD_COMPLETED' and entity_id=?",Long.class,id)).isEqualTo(1);
    }

    @Test void invalidObjectMagicFailsAndRemovesOnlyTheRejectedTestObject() throws Exception {
        var owner=activeUser("Invalid upload owner"); String bearer="Bearer "+issuer.issue(owner.getId(),device()).accessToken().value();
        var upload=createUpload(bearer,8); assertThat(put(upload,new byte[]{1,2,3,4,5,6,7,8})).isEqualTo(200);
        UUID id=UUID.fromString(upload.path("mediaId").asText()); String key=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/api/v1/media/"+id+"/complete")
                .header("Authorization",bearer).header("Idempotency-Key",key)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_VERIFICATION_FAILED"));
        var asset=media.findById(id).orElseThrow(); assertThat(asset.getMediaStatus()).isEqualTo(MediaStatus.FAILED);
        assertThatThrownBy(()->s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(asset.getStorageKey()).build()))
                .isInstanceOf(S3Exception.class).satisfies(error->assertThat(((S3Exception)error).statusCode()).isEqualTo(404));
    }
    private com.fasterxml.jackson.databind.JsonNode createUpload(String bearer,int size) throws Exception {
        var response=mvc.perform(post("/api/v1/media/uploads").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"USER_AVATAR\",\"contentType\":\"image/png\",\"fileSize\":"+size+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    }
    private int put(com.fasterxml.jackson.databind.JsonNode upload,byte[] bytes) throws Exception {
        String url=upload.path("uploadUrl").asText(); assertThat(url).startsWith(endpoint().toString());
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        upload.path("requiredHeaders").fields().forEachRemaining(header->request.header(header.getKey(),header.getValue().asText()));
        return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    @org.springframework.boot.test.context.TestConfiguration static class TestStorageClients {
        static StaticCredentialsProvider credentials() { return StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key","test-secret-key")); }
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        S3Client isolatedS3Client() { return S3Client.builder().endpointOverride(endpoint()).region(Region.US_EAST_1)
                .credentialsProvider(credentials()).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build(); }
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        S3Presigner isolatedS3Presigner() { return S3Presigner.builder().endpointOverride(endpoint()).region(Region.US_EAST_1)
                .credentialsProvider(credentials()).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build(); }
    }
}
