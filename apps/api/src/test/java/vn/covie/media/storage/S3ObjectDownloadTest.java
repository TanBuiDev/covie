package vn.covie.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectDownloadTest {
    @TempDir Path directory;
    private final S3Client client = mock(S3Client.class);
    private final S3ObjectStorage storage = new S3ObjectStorage(
            new StorageProperties("video-fixture", "us-east-1", null, null, false,
                    Duration.ofMinutes(5), 10_485_760, 20), client, mock(S3Presigner.class));

    @Test void copiesExactObjectBytesWithoutRangeOrExternalUrl() throws Exception {
        byte[] expected = new byte[20_000];
        for (int position = 0; position < expected.length; position++) expected[position] = (byte) position;
        var stream = object(expected);
        Path target = directory.resolve("probe.mp4");

        storage.downloadTo("owned/video.mp4", target, expected.length);

        assertThat(Files.readAllBytes(target)).containsExactly(expected);
        verify(client).getObject(GetObjectRequest.builder().bucket("video-fixture").key("owned/video.mp4").build());
        verify(stream, never()).abort();
    }

    @Test void rejectsOverlongObjectAndRemovesPartialFile() {
        var stream = object(new byte[10]);
        Path target = directory.resolve("overlong.mp4");

        assertThatThrownBy(() -> storage.downloadTo("owned/video.mp4", target, 9))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(target).doesNotExist();
        verify(stream).abort();
    }

    @Test void rejectsTruncatedObjectAndRemovesPartialFile() {
        var stream = object(new byte[9]);
        Path target = directory.resolve("truncated.webm");

        assertThatThrownBy(() -> storage.downloadTo("owned/video.webm", target, 10))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(target).doesNotExist();
        verify(stream).abort();
    }

    @Test void neverOverwritesOrDeletesAnExistingTarget() throws Exception {
        var stream = object(new byte[10]);
        Path target = directory.resolve("existing.mp4");
        Files.writeString(target, "preserve existing data");

        assertThatThrownBy(() -> storage.downloadTo("owned/video.mp4", target, 10))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(Files.readString(target)).isEqualTo("preserve existing data");
        verify(stream).abort();
    }

    @Test void rejectsNonPositiveBoundBeforeStorageAccess() {
        assertThatThrownBy(() -> storage.downloadTo("owned/video.mp4", directory.resolve("invalid"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).getObject(any(GetObjectRequest.class));
    }

    private ResponseInputStream<GetObjectResponse> object(byte[] bytes) {
        var stream = spy(new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) bytes.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        return stream;
    }
}
