package vn.covie.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectRangeTest {
    private final S3Client client = mock(S3Client.class);
    private final S3ObjectStorage storage = new S3ObjectStorage(
            new StorageProperties("range-fixture", "us-east-1", null, null, false,
                    Duration.ofMinutes(5), 10_485_760, 20), client, mock(S3Presigner.class));

    @Test void opensOnlyExactOwnedRangeAndAbortsOnEarlyClose() throws Exception {
        var source = object("bytes 2-5/10", 4L);
        try (var input = storage.openRange("private/clip", 2, 5, 10)) {
            assertThat(input.read()).isEqualTo(2);
        }
        verify(client).getObject(GetObjectRequest.builder().bucket("range-fixture").key("private/clip").range("bytes=2-5").build());
        verify(source).abort();
    }

    @Test void rejectsIgnoredOrMismatchedStorageRangeBeforeReturningBytes() {
        var ignored = object(null, 10L);
        assertThatThrownBy(() -> storage.openRange("private/clip", 2, 5, 10)).isInstanceOf(UncheckedIOException.class);
        verify(ignored).abort();
        var wrongTotal = object("bytes 2-5/11", 4L);
        assertThatThrownBy(() -> storage.openRange("private/clip", 2, 5, 10)).isInstanceOf(UncheckedIOException.class);
        verify(wrongTotal).abort();
        var wrongLength = object("bytes 2-5/10", 5L);
        assertThatThrownBy(() -> storage.openRange("private/clip", 2, 5, 10)).isInstanceOf(UncheckedIOException.class);
        verify(wrongLength).abort();
    }

    @Test void rejectsInvalidBoundsWithoutCallingStorage() {
        assertThatThrownBy(() -> storage.openRange("private/clip", -1, 5, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.openRange("private/clip", 6, 5, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.openRange("private/clip", 0, 10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.openRange("private/clip", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    private ResponseInputStream<GetObjectResponse> object(String contentRange, Long length) {
        var source = spy(new ResponseInputStream<>(GetObjectResponse.builder().contentRange(contentRange).contentLength(length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[]{2, 3, 4, 5}))));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(source);
        return source;
    }
}
