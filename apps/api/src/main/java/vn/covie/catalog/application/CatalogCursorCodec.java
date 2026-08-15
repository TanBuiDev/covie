package vn.covie.catalog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/** Encodes cursor positions as an opaque, validated transport value. */
public final class CatalogCursorCodec {
    private final ObjectMapper json;

    public CatalogCursorCodec() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    CatalogCursorCodec(ObjectMapper json) {
        this.json = json;
    }

    public String encode(CatalogCursor cursor) {
        return encode(cursor, null);
    }

    public String encode(CatalogCursor cursor, String context) {
        validate(cursor);
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.writeValueAsBytes(new CursorEnvelope(cursor, context)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cursor cannot be encoded.", exception);
        }
    }

    public CatalogCursor decode(String encoded, CatalogSort requestedSort) {
        return decode(encoded, requestedSort, null);
    }

    public CatalogCursor decode(String encoded, CatalogSort requestedSort, String context) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            CursorEnvelope envelope = json.readValue(Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII)),
                    CursorEnvelope.class);
            if (!java.util.Objects.equals(envelope.context(), context)) {
                throw invalid();
            }
            CatalogCursor cursor = envelope.cursor();
            validate(cursor);
            if (cursor.sort() != requestedSort) {
                throw invalid();
            }
            return cursor;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static void validate(CatalogCursor cursor) {
        if (cursor == null || cursor.sort() == null || cursor.itemId() == null) {
            throw new IllegalArgumentException("Cursor shape is incomplete.");
        }
        boolean valid = switch (cursor.sort()) {
            case RELEVANCE -> cursor.rank() != null && cursor.distanceMeters() != null;
            case DISTANCE -> cursor.distanceMeters() != null;
            case PRICE_ASC, PRICE_DESC -> cursor.price() != null;
            case NEWEST -> cursor.createdAt() != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("Cursor shape does not match its sort.");
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.CATALOG_FILTER_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                "The catalog cursor is invalid for this query.");
    }

    private record CursorEnvelope(CatalogCursor cursor, String context) {}
}
