package vn.covie.reviews.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

public final class ReviewCursorCodec {
    private final ObjectMapper mapper;
    public ReviewCursorCodec(ObjectMapper mapper) { this.mapper = mapper; }
    public String encode(ReviewCursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor)); }
        catch (Exception exception) { throw new IllegalArgumentException("Review cursor cannot be encoded.", exception); }
    }
    public ReviewCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            ReviewCursor cursor = mapper.readValue(Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII)), ReviewCursor.class);
            if (cursor.v() != 1 || cursor.createdAt() == null || cursor.reviewId() == null) throw invalid();
            return cursor;
        } catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw invalid(); }
    }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Review cursor is invalid."); }
}
