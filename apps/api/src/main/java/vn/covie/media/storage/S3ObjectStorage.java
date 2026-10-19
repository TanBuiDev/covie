package vn.covie.media.storage;

import java.net.URI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final StorageProperties properties;
    private final S3Client s3;
    private final S3Presigner presigner;

    public S3ObjectStorage(StorageProperties properties, S3Client s3, S3Presigner presigner) {
        this.properties = properties;
        this.s3 = s3;
        this.presigner = presigner;
    }

    @Override
    public PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .ifNoneMatch("*")
                .build();
        PutObjectPresignRequest signRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(signRequest);

        return new PresignedUpload(
                URI.create(signed.url().toString()),
                signed.httpRequest().method().name(),
                signed.expiration(),
                requiredHeaders(signed));
    }

    @Override
    public StoredObjectMetadata head(String key) {
        HeadObjectResponse object = s3.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
        return new StoredObjectMetadata(object.contentType(), object.contentLength());
    }

    @Override
    public byte[] readPrefix(String key, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .range("bytes=0-" + (maxBytes - 1))
                .build();
        return s3.getObjectAsBytes(request).asByteArray();
    }

    @Override
    public java.io.InputStream openRange(String key, long first, long last, long totalBytes) {
        if (first < 0 || last < first || last >= totalBytes) throw new IllegalArgumentException("Invalid object range.");
        var object = s3.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key)
                .range("bytes=" + first + "-" + last).build());
        String expectedRange = "bytes " + first + "-" + last + "/" + totalBytes;
        if (!Long.valueOf(last - first + 1).equals(object.response().contentLength())
                || !expectedRange.equals(object.response().contentRange())) {
            object.abort();
            throw new UncheckedIOException(new IOException("Stored object range does not match verified metadata."));
        }
        return new java.io.FilterInputStream(object) {
            @Override public void close() { object.abort(); }
        };
    }

    @Override
    public void downloadTo(String key, Path target, long expectedBytes) {
        download(key, target, expectedBytes, null);
    }

    @Override
    public void downloadTo(String key, Path target, long expectedBytes, Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        try (var deadline = new DownloadDeadline(timeout)) {
            download(key, target, expectedBytes, deadline);
        }
    }

    private void download(String key, Path target, long expectedBytes, DownloadDeadline deadline) {
        if (expectedBytes <= 0) throw new IllegalArgumentException("expectedBytes must be positive");
        ResponseInputStream<GetObjectResponse> object = null;
        boolean created = false;
        try {
            var request = GetObjectRequest.builder().bucket(properties.bucket()).key(key);
            if (deadline != null) request.overrideConfiguration(config -> config.apiCallTimeout(deadline.timeout).apiCallAttemptTimeout(deadline.timeout));
            object = s3.getObject(request.build());
            if (deadline != null) {
                deadline.stream.set(object);
                deadline.requireActive();
            }
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                byte[] buffer = new byte[8192];
                long copied = 0;
                int count;
                while ((count = object.read(buffer, 0, (int) Math.min(buffer.length, Math.max(1, expectedBytes - copied)))) != -1) {
                    if (deadline != null) deadline.requireActive();
                    if (count > expectedBytes - copied) throw new IOException("Stored object exceeds expected size.");
                    output.write(buffer, 0, count);
                    copied += count;
                }
                if (copied != expectedBytes) throw new IOException("Stored object is shorter than expected size.");
                if (deadline != null) deadline.requireActive();
            }
            object.close();
        } catch (IOException | RuntimeException failure) {
            if (object != null) {
                try { object.abort(); } catch (RuntimeException abortFailure) { failure.addSuppressed(abortFailure); }
            }
            if (created) {
                try { Files.deleteIfExists(target); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            if (failure instanceof IOException ioFailure) throw new UncheckedIOException("Object download failed.", ioFailure);
            throw (RuntimeException) failure;
        }
    }

    private static final class DownloadDeadline implements AutoCloseable {
        private final Duration timeout;
        private final java.util.concurrent.atomic.AtomicReference<ResponseInputStream<GetObjectResponse>> stream = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean expired = new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("covie-video-download-deadline").factory());
        private final java.util.concurrent.ScheduledFuture<?> expiry;

        private DownloadDeadline(Duration timeout) {
            this.timeout = timeout;
            expiry = timer.schedule(() -> {
                expired.set(true);
                var current = stream.get();
                if (current != null) current.abort();
            }, timeout.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
        }

        private void requireActive() throws IOException {
            if (expired.get()) throw new IOException("Object download deadline exceeded.");
        }

        @Override public void close() {
            expiry.cancel(false);
            timer.shutdownNow();
        }
    }

    @Override
    public void deleteIfExists(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public void deleteIfExists(String key, Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key)
                .overrideConfiguration(config -> config.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout)).build());
    }

    private static Map<String, String> requiredHeaders(PresignedPutObjectRequest signed) {
        Map<String, String> headers = new LinkedHashMap<>();
        signed.httpRequest().headers().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length") && !values.isEmpty()) {
                headers.put(name, values.getFirst());
            }
        });
        return Map.copyOf(headers);
    }
}
