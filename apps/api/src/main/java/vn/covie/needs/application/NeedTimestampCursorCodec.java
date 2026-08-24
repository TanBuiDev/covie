package vn.covie.needs.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

public final class NeedTimestampCursorCodec {
    private final ObjectMapper mapper;
    public NeedTimestampCursorCodec(ObjectMapper mapper) { this.mapper = mapper; }
    public String encode(NeedTimestampCursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor)); }
        catch (Exception exception) { throw new IllegalArgumentException("Cursor cannot be encoded.", exception); }
    }
    public NeedTimestampCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            NeedTimestampCursor cursor = mapper.readValue(Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII)), NeedTimestampCursor.class);
            if (cursor.v() != 1 || cursor.createdAt() == null || cursor.id() == null) throw invalid();
            return cursor;
        } catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw invalid(); }
    }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Need cursor is invalid."); }
}
