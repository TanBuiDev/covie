package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogCursor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class PublicMerchantCatalogCursorCodecTest {
    private final PublicMerchantCatalogCursorCodec codec = new PublicMerchantCatalogCursorCodec();

    @Test
    void roundTripsPositionWithinMerchantScope() {
        UUID merchantId = UUID.randomUUID();
        PublicMerchantCatalogCursor cursor = new PublicMerchantCatalogCursor(
                Instant.parse("2026-01-01T00:00:00Z"), UUID.randomUUID());

        assertThat(codec.decode(codec.encode(cursor, merchantId), merchantId)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedOrCrossMerchantCursorAsCatalogFilterInvalid() {
        UUID merchantId = UUID.randomUUID();
        String encoded = codec.encode(new PublicMerchantCatalogCursor(
                Instant.parse("2026-01-01T00:00:00Z"), UUID.randomUUID()), merchantId);

        assertInvalid(() -> codec.decode("not-a-cursor", merchantId));
        assertInvalid(() -> codec.decode(encoded, UUID.randomUUID()));
    }

    private static void assertInvalid(ThrowingCommand command) {
        assertThatThrownBy(command::run).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.CATALOG_FILTER_INVALID);
    }

    @FunctionalInterface
    private interface ThrowingCommand { void run(); }
}
