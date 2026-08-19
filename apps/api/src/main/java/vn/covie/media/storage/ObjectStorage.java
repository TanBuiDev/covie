package vn.covie.media.storage;

import java.time.Duration;

public interface ObjectStorage {
    PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl);

    StoredObjectMetadata head(String key);

    byte[] readPrefix(String key, int maxBytes);

    void deleteIfExists(String key);
}
