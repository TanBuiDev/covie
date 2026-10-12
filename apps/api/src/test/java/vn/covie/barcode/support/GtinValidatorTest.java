package vn.covie.barcode.support;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GtinValidatorTest {

    @Test
    void validatesStandardGtinCodes() {
        // Vinamilk 8934673501354 (valid EAN-13)
        assertThat(GtinValidator.isValidGtin("8934673501354")).isTrue();
        // Vinamilk Green Farm 8934673100458
        assertThat(GtinValidator.isValidGtin("8934673100458")).isTrue();
        // TH True Milk 8935217400393
        assertThat(GtinValidator.isValidGtin("8935217400393")).isTrue();
        // UPC-12 Milano cookies 0014100047957 padded
        assertThat(GtinValidator.isValidGtin("014100047957")).isTrue();
        // 8-digit EAN-8
        assertThat(GtinValidator.isValidGtin("00635424")).isTrue();
    }

    @Test
    void rejectsInvalidGtinCodes() {
        assertThat(GtinValidator.isValidGtin(null)).isFalse();
        assertThat(GtinValidator.isValidGtin("")).isFalse();
        assertThat(GtinValidator.isValidGtin("123")).isFalse();
        // Bad checksum: change last digit 4 -> 5
        assertThat(GtinValidator.isValidGtin("8934673501355")).isFalse();
        // Non-digits
        assertThat(GtinValidator.isValidGtin("893467350135a")).isFalse();
    }

    @Test
    void formatsGtin14() {
        assertThat(GtinValidator.toGtin14("8934673501354")).isEqualTo("08934673501354");
        assertThat(GtinValidator.toGtin14("0014100047957")).isEqualTo("00014100047957");
    }
}
