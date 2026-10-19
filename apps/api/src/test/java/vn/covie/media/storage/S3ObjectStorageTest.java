package vn.covie.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Testcontainers
class S3ObjectStorageTest {

    private static final String BUCKET = "media-storage-test";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADElEQVR42mP4z8DwHwAFAP+JmT0dAAAAAElFTkSuQmCC");

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    DockerImageName.parse(System.getProperty("covie.test.minio-image",
                            "quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")))
            .withEnv("MINIO_ROOT_USER", "test-access-key")
            .withEnv("MINIO_ROOT_PASSWORD", "test-secret-key")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    private static S3Client s3;
    private static S3Presigner presigner;
    private static S3ObjectStorage storage;

    @BeforeAll
    static void setUpStorage() {
        URI endpoint = URI.create("http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000)));
        S3Configuration configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-access-key", "test-secret-key"));

        s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        storage = new S3ObjectStorage(
                new StorageProperties(BUCKET, "us-east-1", endpoint, endpoint.resolve("/" + BUCKET), true,
                        Duration.ofMinutes(10), 10_485_760, 20),
                s3,
                presigner);
    }

    @AfterAll
    static void closeClients() {
        if (presigner != null) {
            presigner.close();
        }
        if (s3 != null) {
            s3.close();
        }
    }

    @Test
    void deletesOwnedObjectsWithDeadlineAndKeepsDeletionIdempotent() {
        String key = "users/disposable-cleanup/video.bin";
        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(), RequestBody.fromBytes(new byte[]{1}));
        assertThatThrownBy(() -> storage.deleteIfExists(key, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.head(key).contentLength()).isEqualTo(1);
        storage.deleteIfExists(key, Duration.ofSeconds(5));
        assertThatThrownBy(() -> storage.head(key)).isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class)
                .satisfies(failure -> assertThat(((software.amazon.awssdk.services.s3.model.S3Exception) failure).statusCode()).isEqualTo(404));
        storage.deleteIfExists(key, Duration.ofSeconds(5));
    }

    @Test
    void streamsExactRangesFromActualStoredObject() throws Exception {
        String key = "users/disposable-range/video.bin";
        byte[] bytes = new byte[20_000];
        for (int position = 0; position < bytes.length; position++) bytes[position] = (byte) position;
        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(), RequestBody.fromBytes(bytes));
        try (var input = storage.openRange(key, 8190, 16390, bytes.length)) {
            assertThat(input.readAllBytes()).containsExactly(java.util.Arrays.copyOfRange(bytes, 8190, 16391));
        }
        try (var input = storage.openRange(key, 0, bytes.length - 1, bytes.length)) {
            assertThat(input.readAllBytes()).containsExactly(bytes);
        }
        assertThatThrownBy(() -> storage.openRange(key, 0, 10, bytes.length + 1))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void downloadsCompleteOwnedObjectForProbeWithExactSizeBounds(@TempDir Path directory) throws Exception {
        String key = "users/disposable-probe/object.bin";
        byte[] bytes = new byte[20_000];
        for (int position = 0; position < bytes.length; position++) bytes[position] = (byte) position;
        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).contentType("application/octet-stream").build(),
                RequestBody.fromBytes(bytes));

        Path target = directory.resolve("complete.bin");
        storage.downloadTo(key, target, bytes.length, Duration.ofSeconds(5));
        assertThat(Files.readAllBytes(target)).containsExactly(bytes);

        Path overlong = directory.resolve("overlong.bin");
        assertThatThrownBy(() -> storage.downloadTo(key, overlong, bytes.length - 1))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(overlong).doesNotExist();

        Path truncated = directory.resolve("truncated.bin");
        assertThatThrownBy(() -> storage.downloadTo(key, truncated, bytes.length + 1))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(truncated).doesNotExist();
    }

    @Test
    void presignedPutStoresImageWithInspectableMetadataAndPrefix() throws Exception {
        String key = "users/9b78180e-3c09-4054-a3b5-0e50dc114026/upload.png";

        PresignedUpload upload = storage.presignPut(key, "image/png", PNG.length, Duration.ofMinutes(2));
        HttpRequest.Builder put = HttpRequest.newBuilder(upload.url())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
        upload.requiredHeaders().forEach(put::header);

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(put.build(), HttpResponse.BodyHandlers.discarding());

        StoredObjectMetadata metadata = storage.head(key);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upload.method()).isEqualTo("PUT");
        assertThat(upload.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(upload.requiredHeaders()).isEqualTo(Map.of("Content-Type", "image/png", "If-None-Match", "*"));
        assertThat(metadata.contentType()).isEqualTo("image/png");
        assertThat(metadata.contentLength()).isEqualTo(PNG.length);
        assertThat(storage.readPrefix(key, 8)).containsExactly(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

        byte[] overwriteBytes = PNG.clone();
        overwriteBytes[overwriteBytes.length - 1] ^= 0x01;
        HttpRequest.Builder overwrite = HttpRequest.newBuilder(upload.url())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(overwriteBytes));
        upload.requiredHeaders().forEach(overwrite::header);
        HttpResponse<Void> overwriteResponse = HttpClient.newHttpClient()
                .send(overwrite.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(overwriteResponse.statusCode()).isEqualTo(412);
        assertThat(storage.readPrefix(key, 8)).containsExactly(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }
}
