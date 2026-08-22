package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class ReviewCursorCodecTest {
    private final ReviewCursorCodec codec = new ReviewCursorCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void roundTripsVersionedOpaqueCursor() {
        ReviewCursor cursor = new ReviewCursor(Instant.parse("2026-08-22T00:00:00Z"), UUID.randomUUID());
        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursorAsValidationFailure() {
        assertThatThrownBy(() -> codec.decode("not-a-cursor"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
