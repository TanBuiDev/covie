package vn.covie.needs.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.needs.persistence.NeedEligibleCandidateKey;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class NeedEligibleItemCursorCodec {
    private final ObjectMapper mapper;

    public NeedEligibleItemCursorCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(UUID merchantId, UUID needId, UUID locationId, NeedEligibleCandidateKey after) {
        try {
            var cursor = new Cursor(1, merchantId, needId, locationId,
                    after.catalogItemId(), after.variantId(), after.locationVariantId());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Eligible-item cursor cannot be encoded.", exception);
        }
    }

    public NeedEligibleCandidateKey decode(String value, UUID merchantId, UUID needId, UUID locationId) {
        if (value == null || value.isBlank()) return null;
        try {
            var cursor = mapper.readValue(Base64.getUrlDecoder().decode(
                    value.getBytes(StandardCharsets.US_ASCII)), Cursor.class);
            if (cursor.v() != 1 || !merchantId.equals(cursor.merchantId())
                    || !needId.equals(cursor.needId()) || !locationId.equals(cursor.locationId())
                    || cursor.catalogItemId() == null || cursor.variantId() == null
                    || cursor.locationVariantId() == null) throw invalid();
            return new NeedEligibleCandidateKey(cursor.catalogItemId(), cursor.variantId(),
                    cursor.locationVariantId());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Eligible-item cursor is invalid.");
    }

    private record Cursor(int v, UUID merchantId, UUID needId, UUID locationId,
            UUID catalogItemId, UUID variantId, UUID locationVariantId) {}
}
