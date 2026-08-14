package vn.covie.media.storage;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
    public void deleteIfExists(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
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
