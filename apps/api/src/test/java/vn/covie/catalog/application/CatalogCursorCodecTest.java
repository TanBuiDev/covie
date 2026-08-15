package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.shared.web.ApiException;

class CatalogCursorCodecTest {

    @Test
    void decodesOpaqueCursorOnlyForItsOriginalSort() {
        CatalogCursor cursor = new CatalogCursor(CatalogSort.PRICE_ASC, null, 300L, 25_000L,
                null, UUID.fromString("00000000-0000-0000-0000-000000000042"));
        CatalogCursorCodec codec = new CatalogCursorCodec();

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("PRICE_ASC");
        assertThat(codec.decode(encoded, CatalogSort.PRICE_ASC)).isEqualTo(cursor);
        assertThatThrownBy(() -> codec.decode(encoded, CatalogSort.DISTANCE))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code().name())
                .isEqualTo("CATALOG_FILTER_INVALID");
    }

    @Test
    void rejectsMalformedAndIncompleteCursorPayloads() {
        CatalogCursorCodec codec = new CatalogCursorCodec();

        assertThatThrownBy(() -> codec.decode("not-a-cursor", CatalogSort.NEWEST))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.encode(new CatalogCursor(CatalogSort.NEWEST, BigDecimal.ONE,
                null, null, Instant.parse("2026-01-01T00:00:00Z"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCursorWhenTheFilterAndLocationContextChanged() {
        CatalogCursorCodec codec = new CatalogCursorCodec();
        CatalogCursor cursor = new CatalogCursor(CatalogSort.DISTANCE, null, 300L, null, null,
                UUID.fromString("00000000-0000-0000-0000-000000000043"));

        String encoded = codec.encode(cursor, "latitude=10.8;radius=5000;type=PRODUCT");

        assertThatThrownBy(() -> codec.decode(encoded, CatalogSort.DISTANCE,
                "latitude=10.8;radius=5000;type=SERVICE"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code().name())
                .isEqualTo("CATALOG_FILTER_INVALID");
    }
}
