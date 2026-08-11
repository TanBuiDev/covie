package vn.covie.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesIsoCurrencyCode() {
        Money money = new Money(45_000, "vnd");

        assertThat(money.amount()).isEqualTo(45_000);
        assertThat(money.currency()).isEqualTo("VND");
    }

    @Test
    void rejectsUnknownCurrencyCode() {
        assertThatThrownBy(() -> new Money(10, "XYZ_NOT_REAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
