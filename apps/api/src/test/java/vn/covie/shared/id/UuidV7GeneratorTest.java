package vn.covie.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generatesRfc4122VariantVersion7Uuid() {
        UUID id = new UuidV7Generator().generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentUnixMillisecondsInMostSignificantBits() {
        long before = System.currentTimeMillis();
        UUID id = new UuidV7Generator().generate();
        long after = System.currentTimeMillis();

        long encodedMillis = (id.getMostSignificantBits() >>> 16) & 0x0000FFFFFFFFFFFFL;
        assertThat(encodedMillis).isBetween(before, after);
    }
}
