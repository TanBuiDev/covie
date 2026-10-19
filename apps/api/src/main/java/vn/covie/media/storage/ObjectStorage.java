package vn.covie.media.storage;

import java.time.Duration;
import java.nio.file.Path;

public interface ObjectStorage {
    PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl);

    StoredObjectMetadata head(String key);

    byte[] readPrefix(String key, int maxBytes);

    default java.io.InputStream openRange(String key, long first, long last, long totalBytes) {
        throw new UnsupportedOperationException("Object range streaming is not supported.");
    }

    default void downloadTo(String key, Path target, long expectedBytes) {
        throw new UnsupportedOperationException("Bounded object download is not supported.");
    }

    default void downloadTo(String key, Path target, long expectedBytes, Duration timeout) {
        throw new UnsupportedOperationException("Deadline-bounded object download is not supported.");
    }

    void deleteIfExists(String key);

    default void deleteIfExists(String key, Duration timeout) {
        throw new UnsupportedOperationException("Deadline-bounded object deletion is not supported.");
    }
}
