package vn.covie.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3VideoDownloadDeadlineTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void boundsStalledHeadersAndBodyAndRemovesOnlyOwnedPartialFile(boolean stalledHeaders) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        try (var handlers = Executors.newVirtualThreadPerTaskExecutor(); var caller = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(handlers);
            server.createContext("/", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().add("Content-Type", "video/mp4");
                    if (exchange.getRequestMethod().equals("HEAD")) {
                        exchange.getResponseHeaders().add("Content-Length", "4");
                        exchange.sendResponseHeaders(200, -1);
                        return;
                    }
                    entered.countDown();
                    if (stalledHeaders) release.await(5, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(200, 4);
                    exchange.getResponseBody().write(1);
                    exchange.getResponseBody().flush();
                    if (!stalledHeaders) release.await(5, TimeUnit.SECONDS);
                    exchange.getResponseBody().write(new byte[]{2, 3, 4});
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (java.io.IOException clientAborted) { }
            });
            server.start();
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort());
            try (var client = S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("fixture", "fixture-only")))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
                    var presigner = S3Presigner.builder().region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("fixture", "fixture-only"))).build()) {
                client.headObject(HeadObjectRequest.builder().bucket("fixture").key("video").build());
                var storage = new S3ObjectStorage(new StorageProperties("fixture", "us-east-1", endpoint, null, true,
                        Duration.ofMinutes(5), 50 * 1024 * 1024, 20), client, presigner);
                Path target = directory.resolve("owned-video");
                var task = caller.submit(() -> storage.downloadTo("video", target, 4, Duration.ofMillis(600)));
                try {
                    assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> task.get(1500, TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class);
                    assertThat(target).doesNotExist();
                } finally {
                    release.countDown();
                    try { task.get(3, TimeUnit.SECONDS); } catch (java.util.concurrent.ExecutionException expectedFailure) { }
                }
            } finally {
                release.countDown();
                server.stop(0);
            }
        }
    }
}
